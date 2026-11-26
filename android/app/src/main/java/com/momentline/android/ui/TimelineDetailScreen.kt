package com.momentline.android.ui

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.momentline.android.data.EventOrder
import com.momentline.android.data.IsoDates
import com.momentline.android.data.Timeline
import com.momentline.android.data.TimelineEvent
import com.momentline.android.data.sortedEvents
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineDetailScreen(
    timeline: Timeline,
    viewModel: MomentViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEditTimeline: () -> Unit,
    onOpenEvent: (TimelineEvent) -> Unit
) {
    var query by rememberSaveable(timeline.id) { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var pendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    val order = viewModel.uiState.eventOrders[timeline.id] ?: EventOrder.OCCURRED_DESC
    val keyword = query.trim()
    val filtered = remember(timeline.events, order, keyword) {
        timeline.events.sortedEvents(order).filter {
            keyword.isEmpty() || it.title.contains(keyword, true) || it.body.contains(keyword, true)
        }
    }

    fun handleBack() {
        if (query.isNotEmpty()) query = "" else onBack()
    }
    BackHandler(onBack = ::handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回时间线列表")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneDot(timeline.tone)
                        Spacer(Modifier.width(10.dp))
                        Text(timeline.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = onEditTimeline) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑时间线")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "事件排序")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            EventOrder.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(if (order == option) "✓ ${option.title}" else option.title) },
                                    onClick = {
                                        viewModel.setEventOrder(timeline.id, option)
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新增事件")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索事件标题或备注") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") }
                },
                singleLine = true
            )
            if (keyword.isNotEmpty()) {
                Text(
                    "找到 ${filtered.size} 条事件",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                timeline.events.isEmpty() -> EmptyState(
                    title = "这条时间线还没有事件",
                    description = "记录第一个事件，并为它选择实际发生时间。",
                    actionTitle = "创建首个事件",
                    onAction = onAdd,
                    modifier = Modifier.weight(1f)
                )
                filtered.isEmpty() -> EmptyState(
                    title = "没有找到相关事件",
                    description = "搜索仅匹配当前时间线的标题和备注。",
                    actionTitle = "清除搜索",
                    onAction = { query = "" },
                    modifier = Modifier.weight(1f)
                )
                else -> {
                    val groups = filtered.groupBy {
                        IsoDates.instant(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groups.forEach { (day, events) ->
                            item(key = "day-$day") {
                                Text(
                                    day.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)),
                                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(events, key = { it.id }) { event ->
                                EventCard(
                                    event = event,
                                    tone = timeline.tone,
                                    onClick = { onOpenEvent(event) },
                                    onDelete = { pendingDeletionId = event.id }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    timeline.events.firstOrNull { it.id == pendingDeletionId }?.let { event ->
        DeleteEventDialog(
            event = event,
            onDismiss = { pendingDeletionId = null },
            onConfirm = {
                viewModel.deleteEvent(timeline.id, event.id).onFailure { actionError = it.message }
                pendingDeletionId = null
            }
        )
    }
    actionError?.let { ErrorDialog(message = it, onDismiss = { actionError = null }) }
}

@Composable
private fun EventCard(event: TimelineEvent, tone: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            ToneDot(tone)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatMomentDate(event.occurredAt, "HH:mm"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (event.body.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        event.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除 ${event.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    timeline: Timeline,
    event: TimelineEvent,
    viewModel: MomentViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回所属时间线")
                    }
                },
                title = { Text("事件详情") },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑事件") }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除事件")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(padding).padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneDot(timeline.tone)
                Spacer(Modifier.width(10.dp))
                Text(
                    timeline.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(event.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    DetailField("发生时间", formatMomentDate(event.occurredAt))
                    Spacer(Modifier.height(12.dp))
                    DetailField("创建时间", formatMomentDate(event.createdAt))
                    Spacer(Modifier.height(12.dp))
                    DetailField("更新时间", formatMomentDate(event.updatedAt))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("备注", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    event.body.ifEmpty { "无备注" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (event.body.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showDelete) {
        DeleteEventDialog(
            event = event,
            onDismiss = { showDelete = false },
            onConfirm = {
                viewModel.deleteEvent(timeline.id, event.id)
                    .onSuccess { onDeleted() }
                    .onFailure { actionError = it.message }
                showDelete = false
            }
        )
    }
    actionError?.let { ErrorDialog(message = it, onDismiss = { actionError = null }) }
}

@Composable
private fun DetailField(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(3.dp))
    Text(value, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun DeleteEventDialog(event: TimelineEvent, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这个事件？") },
        text = { Text("“${event.title}”将被永久删除，同时间线的其他事件不受影响。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}