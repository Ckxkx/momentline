package com.momentline.android.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.momentline.android.data.EventDraft
import com.momentline.android.data.IsoDates
import com.momentline.android.data.Timeline
import com.momentline.android.data.TimelineDraft
import com.momentline.android.data.TimelineEvent
import com.momentline.android.data.graphemeLength
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

@Composable
fun TimelineEditorDialog(timeline: Timeline?, viewModel: MomentViewModel, onClose: () -> Unit) {
    val editorKey = timeline?.id ?: "new-timeline"
    val defaultTone = TimelineTones.first()
    var title by rememberSaveable(editorKey) { mutableStateOf(timeline?.title.orEmpty()) }
    var tone by rememberSaveable(editorKey) { mutableStateOf(timeline?.tone ?: defaultTone) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val dirty = title != timeline?.title.orEmpty() || tone != (timeline?.tone ?: defaultTone)

    fun requestClose() {
        if (dirty) showDiscard = true else onClose()
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text(if (timeline == null) "新建时间线" else "编辑时间线", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("时间线名称") },
                    placeholder = { Text("例如：旅行、成长、阅读") },
                    supportingText = { Text("${title.graphemeLength()}/80") },
                    maxLines = 3
                )
                Spacer(Modifier.height(18.dp))
                Text("主题颜色", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                TimelineTones.chunked(4).forEachIndexed { rowIndex, tones ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        tones.forEachIndexed { columnIndex, option ->
                            val index = rowIndex * 4 + columnIndex
                            Box(
                                modifier = Modifier.size(48.dp)
                                    .clip(CircleShape)
                                    .background(toneColor(option))
                                    .border(
                                        width = if (tone == option) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        shape = CircleShape
                                    )
                                    .clickable { tone = option }
                                    .semantics { contentDescription = "主题颜色 ${index + 1}" },
                                contentAlignment = Alignment.Center
                            ) {
                                if (tone == option) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = ::requestClose,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            val result = if (timeline == null) {
                                viewModel.addTimeline(TimelineDraft(title, tone))
                            } else {
                                viewModel.updateTimeline(timeline, TimelineDraft(title, tone))
                            }
                            result.onSuccess { onClose() }.onFailure { errorMessage = it.message }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("保存时间线") }
                }
            }
        }
    }

    BackHandler(onBack = ::requestClose)
    if (showDiscard) DiscardChangesDialog(onDismiss = { showDiscard = false }, onConfirm = onClose)
}

@Composable
fun EventEditorDialog(
    timelineId: String,
    event: TimelineEvent?,
    viewModel: MomentViewModel,
    onClose: () -> Unit
) {
    val editorKey = event?.id ?: "new-event-$timelineId"
    var title by rememberSaveable(editorKey) { mutableStateOf(event?.title.orEmpty()) }
    var body by rememberSaveable(editorKey) { mutableStateOf(event?.body.orEmpty()) }
    var initialMillis by rememberSaveable(editorKey) {
        mutableStateOf(
            event?.occurredAt?.let { IsoDates.instant(it).toEpochMilli() }
                ?: Instant.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
        )
    }
    var occurredAtMillis by rememberSaveable(editorKey) { mutableStateOf(initialMillis) }
    val originalMillis = event?.occurredAt?.let { IsoDates.instant(it).toEpochMilli() } ?: initialMillis
    val dirty = title != event?.title.orEmpty() || body != event?.body.orEmpty() || occurredAtMillis != originalMillis
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val zonedDateTime = Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault())

    fun requestClose() {
        if (dirty) showDiscard = true else onClose()
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text(if (event == null) "新增事件" else "编辑事件", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("事件标题") },
                    placeholder = { Text("给这个事件一个标题") },
                    supportingText = { Text("${title.graphemeLength()}/100") },
                    maxLines = 3
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = occurredAtMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val current = Calendar.getInstance().apply { timeInMillis = occurredAtMillis }
                                current.set(year, month, day)
                                occurredAtMillis = current.timeInMillis
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val updated = Calendar.getInstance().apply { timeInMillis = occurredAtMillis }
                                        updated.set(Calendar.HOUR_OF_DAY, hour)
                                        updated.set(Calendar.MINUTE, minute)
                                        updated.set(Calendar.SECOND, 0)
                                        updated.set(Calendar.MILLISECOND, 0)
                                        occurredAtMillis = updated.timeInMillis
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("发生时间：${zonedDateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))}")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    label = { Text("备注（可选）") },
                    placeholder = { Text("写下值得记住的细节……") },
                    supportingText = { Text("${body.graphemeLength()}/5000，支持换行") }
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = ::requestClose,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            val draft = EventDraft(
                                title = title,
                                body = body,
                                occurredAt = IsoDates.format(Instant.ofEpochMilli(occurredAtMillis))
                            )
                            val result = if (event == null) {
                                viewModel.addEvent(timelineId, draft)
                            } else {
                                viewModel.updateEvent(timelineId, event, draft)
                            }
                            result.onSuccess { onClose() }.onFailure { errorMessage = it.message }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("保存事件") }
                }
            }
        }
    }

    BackHandler(onBack = ::requestClose)
    if (showDiscard) DiscardChangesDialog(onDismiss = { showDiscard = false }, onConfirm = onClose)
}

@Composable
private fun DiscardChangesDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("放弃未保存的更改？") },
        text = { Text("离开后，本次尚未保存的输入将丢失。") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text("放弃更改", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续编辑") } }
    )
}