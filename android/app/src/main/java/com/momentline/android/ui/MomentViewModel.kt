package com.momentline.android.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.momentline.android.data.AppAppearance
import com.momentline.android.data.BackupException
import com.momentline.android.data.ConflictResolution
import com.momentline.android.data.EventDraft
import com.momentline.android.data.EventOrder
import com.momentline.android.data.ExportPayload
import com.momentline.android.data.ImportPreview
import com.momentline.android.data.MomentRepository
import com.momentline.android.data.Timeline
import com.momentline.android.data.TimelineDraft
import com.momentline.android.data.TimelineEvent
import com.momentline.android.data.TimelineOrder

data class MomentUiState(
    val timelines: List<Timeline> = emptyList(),
    val loadError: String? = null,
    val timelineOrder: TimelineOrder = TimelineOrder.CREATED_DESC,
    val eventOrders: Map<String, EventOrder> = emptyMap(),
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val notice: String? = null,
    val noticeId: Long = 0
)

data class ImportSession(val text: String, val preview: ImportPreview)

class MomentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MomentRepository(application)
    private val preferences = application.getSharedPreferences("momentline_preferences", Application.MODE_PRIVATE)
    private val eventOrderPrefix = "event_order_"

    var uiState by mutableStateOf(MomentUiState())
        private set
    var importSession by mutableStateOf<ImportSession?>(null)
        private set
    var pendingExport by mutableStateOf<ExportPayload?>(null)
        private set

    init {
        uiState = uiState.copy(
            timelineOrder = readTimelineOrder(),
            eventOrders = readEventOrders(),
            appearance = readAppearance()
        )
        reload()
    }

    fun reload() {
        uiState = try {
            uiState.copy(timelines = repository.load(), loadError = null)
        } catch (error: Exception) {
            uiState.copy(loadError = error.message ?: "暂时无法读取时间线，请重试；原有数据未被清除")
        }
    }

    fun addTimeline(draft: TimelineDraft): Result<Unit> = runOperation("时间线已保存") {
        repository.addTimeline(draft)
    }

    fun updateTimeline(original: Timeline, draft: TimelineDraft): Result<Unit> = runOperation("时间线更改已保存") {
        repository.updateTimeline(original, draft)
    }

    fun deleteTimeline(id: String): Result<Unit> = runOperation("时间线及其事件已删除") {
        repository.deleteTimeline(id)
    }

    fun addEvent(timelineId: String, draft: EventDraft): Result<Unit> = runOperation("事件已保存") {
        repository.addEvent(timelineId, draft)
    }

    fun updateEvent(timelineId: String, original: TimelineEvent, draft: EventDraft): Result<Unit> =
        runOperation("事件更改已保存") { repository.updateEvent(timelineId, original, draft) }

    fun deleteEvent(timelineId: String, eventId: String): Result<Unit> = runOperation("事件已删除") {
        repository.deleteEvent(timelineId, eventId)
    }

    fun prepareImport(text: String): Result<Unit> {
        return try {
            importSession = ImportSession(text, repository.previewImport(text))
            Result.success(Unit)
        } catch (error: Exception) {
            repository.recordFailure("import", "validate", error)
            Result.failure(error)
        }
    }

    fun dismissImport() {
        importSession = null
    }

    fun applyImport(resolution: ConflictResolution?): Result<Unit> {
        val session = importSession ?: return Result.failure(BackupException("导入会话已结束，请重新选择备份文件"))
        return try {
            val (timelines, result) = repository.importBackup(session.text, resolution)
            uiState = uiState.copy(timelines = timelines, loadError = null)
            importSession = null
            notify(
                "导入完成：时间线新增 ${result.timelines.added}、跳过 ${result.timelines.skipped}、替换 ${result.timelines.replaced}；" +
                    "事件新增 ${result.events.added}、跳过 ${result.events.skipped}、替换 ${result.events.replaced}"
            )
            Result.success(Unit)
        } catch (error: Exception) {
            repository.recordFailure("import", "commit", error)
            Result.failure(error)
        }
    }

    fun prepareExport(): Result<Unit> {
        return try {
            pendingExport = repository.exportBackup()
            Result.success(Unit)
        } catch (error: Exception) {
            repository.recordFailure("export", "create", error)
            Result.failure(error)
        }
    }

    fun clearPendingExport() {
        pendingExport = null
    }

    fun reportFileFailure(operation: String, stage: String, error: Throwable) {
        repository.recordFailure(operation, stage, error)
    }

    fun setTimelineOrder(order: TimelineOrder) {
        if (order == uiState.timelineOrder) return
        if (!preferences.edit().putString("timeline_order", order.name).commit()) {
            notify("排序偏好未能保存，请检查设备存储空间")
            return
        }
        uiState = uiState.copy(timelineOrder = order)
    }

    fun setEventOrder(timelineId: String, order: EventOrder) {
        if (uiState.eventOrders[timelineId] == order) return
        if (!preferences.edit().putString(eventOrderPrefix + timelineId, order.name).commit()) {
            notify("排序偏好未能保存，请检查设备存储空间")
            return
        }
        uiState = uiState.copy(eventOrders = uiState.eventOrders + (timelineId to order))
    }

    fun setAppearance(appearance: AppAppearance) {
        if (appearance == uiState.appearance) return
        if (!preferences.edit().putString("appearance", appearance.name).commit()) {
            notify("外观偏好未能保存，请检查设备存储空间")
            return
        }
        uiState = uiState.copy(appearance = appearance)
    }

    fun clearNotice(id: Long) {
        if (uiState.noticeId == id) uiState = uiState.copy(notice = null)
    }

    private fun runOperation(message: String, operation: () -> List<Timeline>): Result<Unit> {
        return try {
            val timelines = operation()
            uiState = uiState.copy(timelines = timelines, loadError = null)
            notify(message)
            Result.success(Unit)
        } catch (error: Exception) {
            repository.recordFailure("mutation", "validate_or_read", error)
            Result.failure(error)
        }
    }

    private fun notify(message: String) {
        uiState = uiState.copy(notice = message, noticeId = uiState.noticeId + 1)
    }

    private fun readTimelineOrder(): TimelineOrder {
        return preferences.getString("timeline_order", null)
            ?.let { runCatching { TimelineOrder.valueOf(it) }.getOrNull() }
            ?: TimelineOrder.CREATED_DESC
    }

    private fun readEventOrders(): Map<String, EventOrder> {
        return preferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(eventOrderPrefix) && value is String) {
                runCatching { EventOrder.valueOf(value) }.getOrNull()?.let {
                    key.removePrefix(eventOrderPrefix) to it
                }
            } else null
        }.toMap()
    }

    private fun readAppearance(): AppAppearance {
        return preferences.getString("appearance", null)
            ?.let { runCatching { AppAppearance.valueOf(it) }.getOrNull() }
            ?: AppAppearance.SYSTEM
    }
}