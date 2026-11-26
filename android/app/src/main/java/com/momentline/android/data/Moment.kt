package com.momentline.android.data

import kotlinx.serialization.Serializable
import java.text.BreakIterator
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

@Serializable
data class TimelineEvent(
    val id: String,
    val title: String,
    val body: String,
    val occurredAt: String,
    val createdAt: String,
    val updatedAt: String
) {
    fun validated(): TimelineEvent {
        require(id.isNotBlank()) { "事件标识无效或缺失" }
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "标题不能为空" }
        require(trimmedTitle.graphemeLength() <= 100) { "标题最多 100 个字符" }
        require(body.graphemeLength() <= 5000) { "备注最多 5000 个字符" }
        IsoDates.requireValid(occurredAt, "发生时间")
        IsoDates.requireValid(createdAt, "创建时间")
        IsoDates.requireValid(updatedAt, "更新时间")
        require(IsoDates.instant(updatedAt) >= IsoDates.instant(createdAt)) { "更新时间不能早于创建时间" }
        return copy(title = trimmedTitle)
    }
}

@Serializable
data class Timeline(
    val id: String,
    val title: String,
    val tone: String,
    val createdAt: String,
    val events: List<TimelineEvent>
) {
    fun validated(): Timeline {
        require(id.isNotBlank()) { "时间线标识无效或缺失" }
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "请输入时间线名称" }
        require(trimmedTitle.graphemeLength() <= 80) { "名称最多 80 个字符" }
        require(Regex("^#[0-9a-fA-F]{6}$").matches(tone)) { "请选择主题颜色" }
        IsoDates.requireValid(createdAt, "创建时间")
        val validEvents = events.map { it.validated() }
        require(validEvents.map { it.id }.toSet().size == validEvents.size) { "事件标识在时间线内重复" }
        return copy(title = trimmedTitle, tone = tone.uppercase(Locale.ROOT), events = validEvents)
    }
}

data class TimelineDraft(val title: String, val tone: String)

data class EventDraft(
    val title: String,
    val body: String,
    val occurredAt: String
)

enum class TimelineOrder(val title: String) {
    CREATED_DESC("创建时间 · 最新优先"),
    CREATED_ASC("创建时间 · 最早优先"),
    NAME_ASC("名称 · 正序"),
    NAME_DESC("名称 · 倒序")
}

enum class EventOrder(val title: String) {
    OCCURRED_DESC("发生时间 · 最新优先"),
    OCCURRED_ASC("发生时间 · 最早优先")
}

enum class AppAppearance(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

enum class ConflictResolution {
    KEEP_LOCAL,
    USE_BACKUP
}

data class RecordStats(
    val added: Int = 0,
    val duplicates: Int = 0,
    val conflicts: Int = 0,
    val invalid: Int = 0
)

data class ImportPreview(
    val source: List<Timeline>,
    val timelineStats: RecordStats,
    val eventStats: RecordStats
) {
    val hasConflicts: Boolean get() = timelineStats.conflicts > 0 || eventStats.conflicts > 0
}

data class ImportOutcome(
    val added: Int,
    val skipped: Int,
    val replaced: Int
)

data class ImportResult(
    val timelines: ImportOutcome,
    val events: ImportOutcome
)

data class ExportPayload(
    val text: String,
    val filename: String,
    val timelineCount: Int,
    val eventCount: Int
)

fun Timeline.sameMetadata(other: Timeline): Boolean {
    return id == other.id && title == other.title && tone == other.tone &&
        IsoDates.instant(createdAt) == IsoDates.instant(other.createdAt)
}

fun TimelineEvent.sameContent(other: TimelineEvent): Boolean {
    return id == other.id && title == other.title && body == other.body &&
        IsoDates.instant(occurredAt) == IsoDates.instant(other.occurredAt) &&
        IsoDates.instant(createdAt) == IsoDates.instant(other.createdAt) &&
        IsoDates.instant(updatedAt) == IsoDates.instant(other.updatedAt)
}

fun List<Timeline>.sortedTimelines(order: TimelineOrder): List<Timeline> {
    return sortedWith(Comparator { left, right ->
        when (order) {
            TimelineOrder.CREATED_ASC, TimelineOrder.CREATED_DESC -> {
                val created = IsoDates.instant(left.createdAt).compareTo(IsoDates.instant(right.createdAt))
                if (created != 0) {
                    if (order == TimelineOrder.CREATED_ASC) created else -created
                } else {
                    left.id.compareTo(right.id)
                }
            }
            TimelineOrder.NAME_ASC, TimelineOrder.NAME_DESC -> {
                val name = left.title.englishCaseFold().compareTo(right.title.englishCaseFold())
                if (name != 0) {
                    if (order == TimelineOrder.NAME_ASC) name else -name
                } else {
                    val created = IsoDates.instant(right.createdAt).compareTo(IsoDates.instant(left.createdAt))
                    if (created != 0) created else left.id.compareTo(right.id)
                }
            }
        }
    })
}

fun List<TimelineEvent>.sortedEvents(order: EventOrder): List<TimelineEvent> {
    return sortedWith(Comparator { left, right ->
        val occurred = IsoDates.instant(left.occurredAt).compareTo(IsoDates.instant(right.occurredAt))
        if (occurred != 0) {
            if (order == EventOrder.OCCURRED_ASC) occurred else -occurred
        } else {
            val updated = IsoDates.instant(right.updatedAt).compareTo(IsoDates.instant(left.updatedAt))
            if (updated != 0) updated else left.id.compareTo(right.id)
        }
    })
}

fun String.graphemeLength(): Int {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(this)
    var count = 0
    var end = iterator.first()
    while (iterator.next().also { end = it } != BreakIterator.DONE) count += 1
    return count
}

private fun String.englishCaseFold(): String = buildString(length) {
    this@englishCaseFold.forEach { character ->
        append(if (character in 'A'..'Z') character.lowercaseChar() else character)
    }
}

object IsoDates {
    private val output = DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
        .appendOffsetId()
        .toFormatter(Locale.US)
    private val timezonePattern = Regex("(Z|[+-]\\d{2}:\\d{2})$")

    fun format(instant: Instant): String {
        return output.format(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))
    }

    fun instant(value: String): Instant {
        return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    }

    fun isValid(value: String): Boolean {
        return timezonePattern.containsMatchIn(value) && runCatching { instant(value) }.isSuccess
    }

    fun requireValid(value: String, label: String) {
        require(isValid(value)) { "$label 无效或缺少时区" }
    }
}