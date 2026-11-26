package com.momentline.android.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.momentline.android.data.Timeline
import com.momentline.android.data.TimelineOrder
import com.momentline.android.data.sortedTimelines
import kotlinx.coroutines.delay

@Composable
fun MomentApp(
    viewModel: MomentViewModel,
    sharedUri: Uri?,
    onShareConsumed: () -> Unit,
    onReadShared: (Uri) -> String
) {
    var selectedTimelineId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingTimeline by rememberSaveable { mutableStateOf(false) }
    var editingTimelineId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingEvent by rememberSaveable { mutableStateOf(false) }
    var editingEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val state = viewModel.uiState
    val selectedTimeline = state.timelines.firstOrNull { it.id == selectedTimelineId }
    val selectedEvent = selectedTimeline?.events?.firstOrNull { it.id == selectedEventId }

    LaunchedEffect(state.timelines, state.loadError, selectedTimelineId) {
        if (state.loadError == null && selectedTimelineId != null && selectedTimeline == null) {
            selectedTimelineId = null
            selectedEventId = null
            creatingEvent = false
            editingEventId = null
        }
    }

    fun openImport(text: String) {
        viewModel.prepareImport(text).onFailure { errorMessage = it.message ?: "无法读取备份文件" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            selectedTimeline == null -> TimelineScreen(
                viewModel = viewModel,
                onOpen = {
                    selectedTimelineId = it.id
                    selectedEventId = null
                },
                onAdd = { creatingTimeline = true },
                onEdit = { editingTimelineId = it.id },
                onSettings = { showingSettings = true }
            )
            selectedEvent != null -> EventDetailScreen(
                timeline = selectedTimeline,
                event = selectedEvent,
                viewModel = viewModel,
                onBack = { selectedEventId = null },
                onEdit = { editingEventId = selectedEvent.id },
                onDeleted = { selectedEventId = null }
            )
            else -> TimelineDetailScreen(
                timeline = selectedTimeline,
                viewModel = viewModel,
                onBack = { selectedTimelineId = null },
                onAdd = { creatingEvent = true },
                onEditTimeline = { editingTimelineId = selectedTimeline.id },
                onOpenEvent = { selectedEventId = it.id }
            )
        }
        state.notice?.let { message ->
            val noticeId = state.noticeId
            LaunchedEffect(noticeId) {
                delay(3600)
                viewModel.clearNotice(noticeId)
            }
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)) {
                Text(message)
            }
        }
    }

    if (creatingTimeline) {
        TimelineEditorDialog(timeline = null, viewModel = viewModel, onClose = { creatingTimeline = false })
    }
    editingTimelineId?.let { id ->
        state.timelines.firstOrNull { it.id == id }?.let { timeline ->
            TimelineEditorDialog(timeline = timeline, viewModel = viewModel, onClose = { editingTimelineId = null })
        }
    }
    if (creatingEvent && selectedTimeline != null) {
        EventEditorDialog(
            timelineId = selectedTimeline.id,
            event = null,
            viewModel = viewModel,
            onClose = { creatingEvent = false }
        )
    }
    editingEventId?.let { id ->
        selectedTimeline?.events?.firstOrNull { it.id == id }?.let { event ->
            EventEditorDialog(
                timelineId = selectedTimeline.id,
                event = event,
                viewModel = viewModel,
                onClose = { editingEventId = null }
            )
        }
    }
    if (showingSettings) {
        SettingsDialog(
            viewModel = viewModel,
            onClose = { showingSettings = false },
            onImportText = ::openImport
        )
    }
    viewModel.importSession?.let { session ->
        ImportPreviewDialog(
            preview = session.preview,
            onDismiss = viewModel::dismissImport,
            onConfirm = { resolution ->
                viewModel.applyImport(resolution).onFailure { errorMessage = it.message ?: "导入未完成" }
            }
        )
    }
    sharedUri?.let { uri ->
        AlertDialog(
            onDismissRequest = onShareConsumed,
            title = { Text("接收备份文件？") },
            text = { Text("仅会读取并校验这个分享文件，最终确认导入前不会更改本地数据。") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        openImport(onReadShared(uri))
                    } catch (error: Exception) {
                        viewModel.reportFileFailure("import", "shared_read", error)
                        errorMessage = error.message ?: "无法读取所选文件，请重新选择备份文件"
                    }
                    onShareConsumed()
                }) { Text("接收并检查") }
            },
            dismissButton = { TextButton(onClick = onShareConsumed) { Text("取消") } }
        )
    }
    errorMessage?.let { ErrorDialog(message = it, onDismiss = { errorMessage = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: MomentViewModel,
    onOpen: (Timeline) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Timeline) -> Unit,
    onSettings: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var pendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    val state = viewModel.uiState
    val keyword = query.trim()
    val filtered = remember(state.timelines, state.timelineOrder, keyword) {
        state.timelines.sortedTimelines(state.timelineOrder).filter {
            keyword.isEmpty() || it.title.contains(keyword, ignoreCase = true)
        }
    }

    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moment Line", fontWeight = FontWeight.SemiBold) },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "时间线排序")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            TimelineOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(if (state.timelineOrder == order) "✓ ${order.title}" else order.title) },
                                    onClick = {
                                        viewModel.setTimelineOrder(order)
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置与数据")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新建时间线")
            }
        }
    ) { padding ->
        when {
            state.loadError != null -> EmptyState(
                title = "无法读取时间线",
                description = state.loadError,
                actionTitle = "重试",
                onAction = viewModel::reload,
                modifier = Modifier.padding(padding)
            )
            state.timelines.isEmpty() -> EmptyState(
                title = "为主题建立时间线",
                description = "按旅行、成长或任何主题收藏事件。\n数据仅保存在当前设备。",
                actionTitle = "创建首条时间线",
                onAction = onAdd,
                modifier = Modifier.padding(padding)
            )
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索时间线名称") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") }
                    },
                    singleLine = true
                )
                if (keyword.isNotEmpty()) {
                    Text(
                        "找到 ${filtered.size} 条时间线",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (filtered.isEmpty()) {
                    EmptyState(
                        title = "没有找到相关时间线",
                        description = "换个名称关键词，或清除搜索后查看全部时间线。",
                        actionTitle = "清除搜索",
                        onAction = { query = "" },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { timeline ->
                            TimelineCard(
                                timeline = timeline,
                                onClick = { onOpen(timeline) },
                                onEdit = { onEdit(timeline) },
                                onDelete = { pendingDeletionId = timeline.id }
                            )
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    state.timelines.firstOrNull { it.id == pendingDeletionId }?.let { timeline ->
        AlertDialog(
            onDismissRequest = { pendingDeletionId = null },
            title = { Text("删除这条时间线？") },
            text = { Text("“${timeline.title}”及其中 ${timeline.events.size} 条事件将被永久删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTimeline(timeline.id).onFailure { actionError = it.message }
                    pendingDeletionId = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeletionId = null }) { Text("取消") } }
        )
    }
    actionError?.let { ErrorDialog(message = it, onDismiss = { actionError = null }) }
}

@Composable
private fun TimelineCard(
    timeline: Timeline,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToneDot(timeline.tone, Modifier.size(18.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    timeline.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "${timeline.events.size} 条事件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑 ${timeline.title}")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除 ${timeline.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}