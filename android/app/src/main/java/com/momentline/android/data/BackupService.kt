package com.momentline.android.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.Instant

class BackupException(message: String) : Exception(message)

object BackupService {
    private const val FORMAT = "MomentLine"
    private const val VERSION = 2
    private val json = Json { prettyPrint = true }

    fun createBackup(timelines: List<Timeline>, exportedAt: Instant = Instant.now()): String {
        val root = JsonObject(mapOf(
            "format" to JsonPrimitive(FORMAT),
            "version" to JsonPrimitive(VERSION),
            "exportedAt" to JsonPrimitive(IsoDates.format(exportedAt)),
            "timelines" to JsonArray(timelines.map { json.encodeToJsonElement(Timeline.serializer(), it) })
        ))
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun decodeBackup(text: String): List<Timeline> {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            throw BackupException("这不是有效的 Moment Line 备份文件")
        }
        val format = root.string("format")
        val versionPrimitive = root["version"] as? JsonPrimitive
        val version = if (versionPrimitive?.isString == false) versionPrimitive.intOrNull else null
        val exportedAt = root.string("exportedAt")
        if (format != FORMAT || version == null || exportedAt == null || !IsoDates.isValid(exportedAt)) {
            throw BackupException("这不是有效的 Moment Line 备份文件")
        }
        if (version != VERSION) {
            throw BackupException("该备份版本暂不受支持，请更新应用或使用兼容的版本 2 备份")
        }
        val records = root["timelines"] as? JsonArray
            ?: throw BackupException("这不是有效的 Moment Line 备份文件：timelines 集合缺失或格式错误")
        return decodeTimelines(records)
    }

    fun encodeLocal(timelines: List<Timeline>): String {
        val root = JsonObject(mapOf(
            "timelines" to JsonArray(timelines.map { json.encodeToJsonElement(Timeline.serializer(), it) })
        ))
        return root.toString()
    }

    fun decodeLocal(text: String): List<Timeline> {
        val records = try {
            json.parseToJsonElement(text).jsonObject["timelines"] as? JsonArray
        } catch (_: Exception) {
            null
        } ?: throw BackupException("本地时间线格式不完整")
        return decodeTimelines(records)
    }

    fun decodeLegacyLocal(text: String): List<TimelineEvent> {
        val records = try {
            json.parseToJsonElement(text) as? JsonArray
        } catch (_: Exception) {
            null
        } ?: throw BackupException("旧版本地记录格式不完整")
        val identifiers = mutableSetOf<String>()
        return records.mapIndexed { index, element ->
            val event = decodeEvent(element, timelineIndex = null, eventIndex = index)
            if (!identifiers.add(event.id)) throw BackupException("第 ${index + 1} 条旧记录有误：事件标识重复")
            event
        }
    }

    fun preview(text: String, local: List<Timeline>): ImportPreview {
        val imported = decodeBackup(text)
        val localById = local.associateBy { it.id }
        var timelinesAdded = 0
        var timelinesDuplicated = 0
        var timelinesConflicted = 0
        var eventsAdded = 0
        var eventsDuplicated = 0
        var eventsConflicted = 0

        imported.forEach { incoming ->
            val existing = localById[incoming.id]
            when {
                existing == null -> timelinesAdded += 1
                existing.sameMetadata(incoming) -> timelinesDuplicated += 1
                else -> timelinesConflicted += 1
            }
            val localEvents = existing?.events?.associateBy { it.id }.orEmpty()
            incoming.events.forEach { event ->
                val localEvent = localEvents[event.id]
                when {
                    localEvent == null -> eventsAdded += 1
                    localEvent.sameContent(event) -> eventsDuplicated += 1
                    else -> eventsConflicted += 1
                }
            }
        }
        return ImportPreview(
            source = imported,
            timelineStats = RecordStats(timelinesAdded, timelinesDuplicated, timelinesConflicted),
            eventStats = RecordStats(eventsAdded, eventsDuplicated, eventsConflicted)
        )
    }

    fun merge(local: List<Timeline>, preview: ImportPreview, resolution: ConflictResolution?): List<Timeline> {
        if (preview.hasConflicts && resolution == null) throw BackupException("请选择冲突处理方式")
        val result = local.toMutableList()
        preview.source.forEach { incoming ->
            val timelineIndex = result.indexOfFirst { it.id == incoming.id }
            if (timelineIndex < 0) {
                result += incoming
                return@forEach
            }
            val existing = result[timelineIndex]
            val mergedEvents = existing.events.toMutableList()
            incoming.events.forEach { incomingEvent ->
                val eventIndex = mergedEvents.indexOfFirst { it.id == incomingEvent.id }
                if (eventIndex < 0) {
                    mergedEvents += incomingEvent
                } else if (resolution == ConflictResolution.USE_BACKUP &&
                    !mergedEvents[eventIndex].sameContent(incomingEvent)
                ) {
                    mergedEvents[eventIndex] = incomingEvent
                }
            }
            val metadata = if (resolution == ConflictResolution.USE_BACKUP && !existing.sameMetadata(incoming)) {
                incoming
            } else {
                existing
            }
            result[timelineIndex] = metadata.copy(events = mergedEvents)
        }
        return result
    }

    fun resultFor(preview: ImportPreview, resolution: ConflictResolution?): ImportResult {
        fun outcome(stats: RecordStats): ImportOutcome {
            val replaced = if (resolution == ConflictResolution.USE_BACKUP) stats.conflicts else 0
            val kept = if (resolution == ConflictResolution.KEEP_LOCAL) stats.conflicts else 0
            return ImportOutcome(
                added = stats.added,
                skipped = stats.duplicates + kept,
                replaced = replaced
            )
        }
        return ImportResult(outcome(preview.timelineStats), outcome(preview.eventStats))
    }

    private fun decodeTimelines(records: JsonArray): List<Timeline> {
        val identifiers = mutableSetOf<String>()
        return records.mapIndexed { index, element ->
            val timeline = decodeTimeline(element, index)
            if (!identifiers.add(timeline.id)) {
                throw BackupException("第 ${index + 1} 条时间线有误：id 在文件中重复")
            }
            timeline
        }
    }

    private fun decodeTimeline(element: kotlinx.serialization.json.JsonElement, index: Int): Timeline {
        fun fail(reason: String): Nothing = throw BackupException("第 ${index + 1} 条时间线有误：$reason")
        val record = element as? JsonObject ?: fail("记录必须是 JSON 对象")
        val id = record.string("id")?.takeIf { it.isNotBlank() } ?: fail("id 无效或缺失")
        val title = record.string("title") ?: fail("title 缺失或不是字符串")
        val tone = record.string("tone") ?: fail("tone 缺失或不是字符串")
        val createdAt = record.string("createdAt") ?: fail("createdAt 缺失或不是字符串")
        val events = record["events"] as? JsonArray ?: fail("events 集合缺失或格式错误")
        val eventIdentifiers = mutableSetOf<String>()
        val decodedEvents = events.mapIndexed { eventIndex, eventElement ->
            val event = try {
                decodeEvent(eventElement, index, eventIndex)
            } catch (error: BackupException) {
                throw error
            }
            if (!eventIdentifiers.add(event.id)) {
                throw BackupException("第 ${index + 1} 条时间线的第 ${eventIndex + 1} 条事件有误：id 在时间线内重复")
            }
            event
        }
        return try {
            Timeline(id, title, tone, createdAt, decodedEvents).validated()
        } catch (error: IllegalArgumentException) {
            fail(error.message ?: "字段校验失败")
        }
    }

    private fun decodeEvent(
        element: kotlinx.serialization.json.JsonElement,
        timelineIndex: Int?,
        eventIndex: Int
    ): TimelineEvent {
        val location = if (timelineIndex == null) {
            "第 ${eventIndex + 1} 条记录"
        } else {
            "第 ${timelineIndex + 1} 条时间线的第 ${eventIndex + 1} 条事件"
        }
        fun fail(reason: String): Nothing = throw BackupException("$location 有误：$reason")
        val record = element as? JsonObject ?: fail("记录必须是 JSON 对象")
        val id = record.string("id")?.takeIf { it.isNotBlank() } ?: fail("id 无效或缺失")
        val title = record.string("title") ?: fail("title 缺失或不是字符串")
        val body = record.string("body") ?: fail("body 缺失或不是字符串")
        val occurredAt = record.string("occurredAt") ?: fail("occurredAt 缺失或不是字符串")
        val createdAt = record.string("createdAt") ?: fail("createdAt 缺失或不是字符串")
        val updatedAt = record.string("updatedAt") ?: fail("updatedAt 缺失或不是字符串")
        return try {
            TimelineEvent(id, title, body, occurredAt, createdAt, updatedAt).validated()
        } catch (error: IllegalArgumentException) {
            fail(error.message ?: "字段校验失败")
        }
    }

    private fun JsonObject.string(name: String): String? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.contentOrNull else null
    }
}

fun decodeUtf8(
    bytes: ByteArray,
    errorMessage: String = "这不是有效的 Moment Line 备份文件：文件必须使用 UTF-8 编码"
): String {
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        throw BackupException(errorMessage)
    }
}

fun ContentResolver.readUtf8(uri: Uri): String {
    val bytes = try {
        openInputStream(uri)?.use { it.readBytes() }
            ?: throw BackupException("无法读取所选文件，请重新选择备份文件")
    } catch (error: BackupException) {
        throw error
    } catch (_: Exception) {
        throw BackupException("无法读取所选文件，请重新选择备份文件")
    }
    return decodeUtf8(bytes)
}