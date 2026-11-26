package com.momentline.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.momentline.android.data.AppAppearance
import com.momentline.android.data.readUtf8

@Composable
fun SettingsDialog(
    viewModel: MomentViewModel,
    onClose: () -> Unit,
    onImportText: (String) -> Unit
) {
    val context = LocalContext.current
    var showExportWarning by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val backup = viewModel.pendingExport
        if (uri == null) {
            if (backup != null) status = "已取消导出，本地数据未更改。"
            viewModel.clearPendingExport()
            return@rememberLauncherForActivityResult
        }
        if (backup == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                output.write(backup.text.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: throw IllegalArgumentException("目的地不可写")
            val filename = queryDisplayName(context, uri) ?: backup.filename
            status = "已导出 $filename：${backup.timelineCount} 条时间线，${backup.eventCount} 条事件。"
            errorMessage = null
        } catch (error: Exception) {
            viewModel.reportFileFailure("export", "write", error)
            errorMessage = "备份文件未能导出，请检查目的地权限或存储空间"
        } finally {
            viewModel.clearPendingExport()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            status = "已取消导入，本地数据未更改。"
            return@rememberLauncherForActivityResult
        }
        try {
            val text = context.contentResolver.readUtf8(uri)
            onClose()
            onImportText(text)
        } catch (error: Exception) {
            viewModel.reportFileFailure("import", "read", error)
            errorMessage = error.message ?: "无法读取所选文件，请重新选择备份文件"
        }
    }
    val timelines = viewModel.uiState.timelines
    val eventCount = timelines.sumOf { it.events.size }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text("设置与数据", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(22.dp))
                Text("外观", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AppAppearance.entries.forEach { appearance ->
                    if (viewModel.uiState.appearance == appearance) {
                        Button(
                            onClick = { viewModel.setAppearance(appearance) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("✓ ${appearance.title}") }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.setAppearance(appearance) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(appearance.title) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
                Text("数据管理", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$eventCount 条事件，分布在 ${timelines.size} 条时间线",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "数据仅保存在当前设备。卸载应用或清除应用数据会删除本地内容，请定期导出完整备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(
                    onClick = { showExportWarning = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("导出全部时间线与事件") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("通过系统文件选择导入") }
                status?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("完成") }
            }
        }
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text("妥善保管备份") },
            text = { Text("备份包含全部时间线标题、事件标题与备注等个人内容。请仅保存到你信任的位置，并妥善保管 JSON 文件。") },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    viewModel.prepareExport()
                        .onSuccess {
                            viewModel.pendingExport?.let { exportLauncher.launch(it.filename) }
                        }
                        .onFailure { errorMessage = it.message ?: "备份文件未能生成，请重试" }
                }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { showExportWarning = false }) { Text("取消") } }
        )
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}