package com.momentline.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.momentline.android.data.ConflictResolution
import com.momentline.android.data.ImportPreview
import com.momentline.android.data.RecordStats

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onDismiss: () -> Unit,
    onConfirm: (ConflictResolution?) -> Unit
) {
    var resolutionName by rememberSaveable { mutableStateOf<String?>(null) }
    val resolution = resolutionName?.let { runCatching { ConflictResolution.valueOf(it) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入预览") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("备份已通过完整校验，最终确认前不会更改本地数据。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                StatsSection("时间线", preview.timelineStats)
                Spacer(Modifier.height(14.dp))
                StatsSection("事件", preview.eventStats)
                if (preview.hasConflicts) {
                    Spacer(Modifier.height(18.dp))
                    Text("选择全部冲突的处理方式", style = MaterialTheme.typography.titleSmall)
                    ConflictResolution.entries.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = resolution == option,
                                onClick = { resolutionName = option.name },
                                role = Role.RadioButton
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = resolution == option, onClick = null)
                            Text(if (option == ConflictResolution.KEEP_LOCAL) "保留本地" else "使用备份")
                        }
                    }
                    Text(
                        when (resolution) {
                            ConflictResolution.USE_BACKUP -> "仅以备份替换冲突的时间线字段和事件内容；新增记录仍会合入。"
                            ConflictResolution.KEEP_LOCAL -> "全部冲突记录保留本地内容；新增记录仍会合入，重复记录跳过。"
                            null -> "未选择前无法确认导入。无论选择哪种方式，都不会删除本地独有数据。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "新增记录将合入，重复记录自动跳过；本地独有数据保持不变。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = preview.timelineStats.invalid == 0 && preview.eventStats.invalid == 0 &&
                    (!preview.hasConflicts || resolution != null),
                onClick = { onConfirm(resolution) }
            ) { Text("确认导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun StatsSection(label: String, stats: RecordStats) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text("新增 ${stats.added} · 重复 ${stats.duplicates} · 冲突 ${stats.conflicts} · 无效 ${stats.invalid}")
}