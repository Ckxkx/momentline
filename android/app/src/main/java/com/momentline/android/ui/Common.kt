package com.momentline.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momentline.android.data.IsoDates
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val TimelineTones = listOf(
    "#4F7C72", "#527A9E", "#806A9B", "#A15F67",
    "#B27745", "#657B45", "#397F8C", "#8A684F"
)

fun toneColor(tone: String): Color = Color(android.graphics.Color.parseColor(tone))

fun formatMomentDate(value: String, pattern: String = "yyyy年M月d日 HH:mm"): String {
    return IsoDates.instant(value).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.CHINA))
}

@Composable
fun ToneDot(tone: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(12.dp), shape = CircleShape, color = toneColor(tone)) {}
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    actionTitle: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionTitle) }
    }
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作未完成") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}