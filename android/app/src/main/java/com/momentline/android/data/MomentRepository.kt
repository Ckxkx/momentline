package com.momentline.android.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class MomentRepository(context: Context) {
    private val directory = File(context.filesDir, "momentline")
    private val file = File(directory, "timelines.json")
    private val atomicFile = AtomicFile(file)
    private val legacyFile = File(directory, "moments.json")
    private val diagnostics = File(directory, "diagnostics.log")
    @Volatile private var readable = true

    @Synchronized
    fun load(): List<Timeline> {
        return try {
            val timelines = when {
                file.exists() || File(directory, "timelines.json.bak").exists() -> {
                    val text = decodeUtf8(
                        atomicFile.openRead().use { it.readBytes() },
                        "本地时间线编码无效"
                    )
                    BackupService.decodeLocal(text)
                }
                legacyFile.exists() -> migrateLegacy()
                else -> emptyList()
            }
            val identifiers = timelines.map { it.id }
            if (identifiers.toSet().size != identifiers.size) throw BackupException("本地时间线标识重复")
            readable = true
            timelines
        } catch (error: Exception) {
            readable = false
            recordDiagnostic("load", "read", error)
            throw if (error is BackupException && error.message?.startsWith("暂时无法") == true) error
            else BackupException("暂时无法读取时间线，请重试；原有数据未被清除")
        }
    }

    @Synchronized
    fun addTimeline(draft: TimelineDraft): List<Timeline> {
        val current = load()
        val timeline = Timeline(
            id = UUID.randomUUID().toString(),
            title = draft.title,
            tone = draft.tone,
            createdAt = IsoDates.format(Instant.now()),
            events = emptyList()
        ).validated()
        val updated = current + timeline
        persist(updated, "add_timeline")
        return updated
    }

    @Synchronized
    fun updateTimeline(original: Timeline, draft: TimelineDraft): List<Timeline> {
        val current = load()
        val index = current.indexOfFirst { it.id == original.id }
        if (index < 0) throw BackupException("这条时间线已不存在，请返回时间线列表")
        val existing = current[index]
        val candidate = existing.copy(title = draft.title, tone = draft.tone).validated()
        if (candidate.sameMetadata(existing)) return current
        val updated = current.toMutableList().also { it[index] = candidate }
        persist(updated, "update_timeline")
        return updated
    }

    @Synchronized
    fun deleteTimeline(id: String): List<Timeline> {
        val current = load()
        if (current.none { it.id == id }) throw BackupException("这条时间线已不存在")
        val updated = current.filterNot { it.id == id }
        persist(updated, "delete_timeline")
        return updated
    }

    @Synchronized
    fun addEvent(timelineId: String, draft: EventDraft): List<Timeline> {
        val current = load()
        val index = current.indexOfFirst { it.id == timelineId }
        if (index < 0) throw BackupException("所属时间线已删除，本次事件修改未保存")
        val now = IsoDates.format(Instant.now())
        val event = TimelineEvent(
            id = UUID.randomUUID().toString(),
            title = draft.title,
            body = draft.body,
            occurredAt = draft.occurredAt,
            createdAt = now,
            updatedAt = now
        ).validated()
        val timeline = current[index]
        val updated = current.toMutableList().also {
            it[index] = timeline.copy(events = timeline.events + event)
        }
        persist(updated, "add_event")
        return updated
    }

    @Synchronized
    fun updateEvent(timelineId: String, original: TimelineEvent, draft: EventDraft): List<Timeline> {
        val current = load()
        val timelineIndex = current.indexOfFirst { it.id == timelineId }
        if (timelineIndex < 0) throw BackupException("所属时间线已删除，本次事件修改未保存")
        val timeline = current[timelineIndex]
        val eventIndex = timeline.events.indexOfFirst { it.id == original.id }
        if (eventIndex < 0) throw BackupException("这个事件已不存在，请返回时间线后重试")
        val existing = timeline.events[eventIndex]
        val candidate = existing.copy(
            title = draft.title,
            body = draft.body,
            occurredAt = draft.occurredAt
        ).validated()
        if (candidate.sameContent(existing)) return current
        val nextUpdate = maxOf(Instant.now(), IsoDates.instant(existing.updatedAt).plusMillis(1))
        val changed = candidate.copy(updatedAt = IsoDates.format(nextUpdate)).validated()
        val events = timeline.events.toMutableList().also { it[eventIndex] = changed }
        val updated = current.toMutableList().also { it[timelineIndex] = timeline.copy(events = events) }
        persist(updated, "update_event")
        return updated
    }

    @Synchronized
    fun deleteEvent(timelineId: String, eventId: String): List<Timeline> {
        val current = load()
        val timelineIndex = current.indexOfFirst { it.id == timelineId }
        if (timelineIndex < 0) throw BackupException("所属时间线已删除，本次事件修改未保存")
        val timeline = current[timelineIndex]
        if (timeline.events.none { it.id == eventId }) throw BackupException("这个事件已不存在")
        val updated = current.toMutableList().also {
            it[timelineIndex] = timeline.copy(events = timeline.events.filterNot { event -> event.id == eventId })
        }
        persist(updated, "delete_event")
        return updated
    }

    @Synchronized
    fun previewImport(text: String): ImportPreview = BackupService.preview(text, load())

    @Synchronized
    fun importBackup(text: String, resolution: ConflictResolution?): Pair<List<Timeline>, ImportResult> {
        val current = load()
        val preview = BackupService.preview(text, current)
        val updated = BackupService.merge(current, preview, resolution)
        persist(updated, "import")
        return updated to BackupService.resultFor(preview, resolution)
    }

    @Synchronized
    fun exportBackup(): ExportPayload {
        val timelines = load()
        val filename = "MomentLine-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json"
        return ExportPayload(
            text = BackupService.createBackup(timelines),
            filename = filename,
            timelineCount = timelines.size,
            eventCount = timelines.sumOf { it.events.size }
        )
    }

    @Synchronized
    fun recordFailure(operation: String, stage: String, error: Throwable) {
        recordDiagnostic(operation, stage, error)
    }

    private fun migrateLegacy(): List<Timeline> {
        val events = BackupService.decodeLegacyLocal(
            decodeUtf8(legacyFile.readBytes(), "旧版本地记录编码无效")
        )
        readable = true
        if (events.isEmpty()) {
            persist(emptyList(), "migrate")
            return emptyList()
        }
        val createdAt = events.minByOrNull { IsoDates.instant(it.createdAt) }?.createdAt
            ?: IsoDates.format(Instant.now())
        val timeline = Timeline(
            id = UUID.randomUUID().toString(),
            title = "我的时间线",
            tone = "#4F7C72",
            createdAt = createdAt,
            events = events
        ).validated()
        persist(listOf(timeline), "migrate")
        return listOf(timeline)
    }

    private fun persist(timelines: List<Timeline>, operation: String) {
        if (!readable) throw BackupException("本地数据不可用，请重试读取后再保存")
        var output: FileOutputStream? = null
        try {
            if (!directory.exists() && !directory.mkdirs()) throw BackupException("无法创建本地目录")
            val bytes = BackupService.encodeLocal(timelines).toByteArray(Charsets.UTF_8)
            output = atomicFile.startWrite()
            output.write(bytes)
            output.flush()
            atomicFile.finishWrite(output)
            output = null
        } catch (error: Exception) {
            if (output != null) atomicFile.failWrite(output)
            recordDiagnostic(operation, "write", error)
            val message = when (operation) {
                "import" -> "导入未完成，本地数据已保持不变"
                "delete_timeline" -> "时间线及其事件未能删除，请重试"
                else -> "暂时无法保存，请检查设备存储空间后重试"
            }
            throw BackupException(message)
        }
    }

    private fun recordDiagnostic(operation: String, stage: String, error: Throwable) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            diagnostics.appendText(
                "${IsoDates.format(Instant.now())} $operation $stage ${error.javaClass.simpleName}\n",
                Charsets.UTF_8
            )
        }
    }
}