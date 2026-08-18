package com.yanparker.modelforum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yanparker.modelforum.ui.theme.participantColor

@Composable
fun ParticipantAvatar(name: String, colorIndex: Int, size: Int = 40) {
    val color = participantColor(colorIndex)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = (size * 0.36).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun StatusDot(status: String, modifier: Modifier = Modifier) {
    val color = when (status) {
        "done", "running" -> Color(0xFF2E7D32)
        "streaming" -> MaterialTheme.colorScheme.primary
        "waiting_limits", "interrupted" -> Color(0xFFF9A825)
        "failed" -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

object DateFmt {
    fun time(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }
}