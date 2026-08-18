package com.yanparker.modelforum.ui.forum

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanparker.modelforum.data.db.MessageEntity
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.ui.Factory
import com.yanparker.modelforum.ui.common.DateFmt
import com.yanparker.modelforum.ui.common.ParticipantAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(
    nav: androidx.navigation.NavHostController,
    container: AppContainer,
    discussionId: Long,
) {
    val vm: com.yanparker.modelforum.ui.ForumViewModel =
        viewModel(key = "forum_$discussionId", factory = Factory.forum(container, discussionId))
    val discussion by vm.discussion.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val participants by vm.participants.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(discussion.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (discussion.state) {
                                "running" -> "модели обсуждают…"
                                "paused" -> "пауза"
                                "waiting_limits" -> "⏳ ожидание лимитов, продолжим сами"
                                "done" -> "завершено"
                                "stopped" -> "остановлено"
                                else -> "готово к запуску"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (discussion.state) {
                                "waiting_limits" -> Color(0xFFF9A825)
                                "done" -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            DiscussionControls(
                state = discussion.state,
                onStart = vm::start,
                onPause = vm::pause,
                onResume = vm::resume,
                onStop = vm::stop,
            )
        },
    ) { inner ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(
                    "Обсуждение начнётся автоматически. Модели будут писать по очереди.\n" +
                        "Тема: «${discussion.title}»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    MessageBubble(m, participants.associate { it.id to it })
                }
            }
        }
    }
}

@Composable
private fun DiscussionControls(
    state: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            "idle", "stopped", "done" -> {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("▶ Запустить снова")
                }
            }
            "running" -> {
                Button(onClick = onPause, modifier = Modifier.weight(1f)) { Text("⏸ Пауза") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("Стоп")
                }
            }
            "paused" -> {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("▶ Продолжить") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Стоп") }
            }
            "waiting_limits" -> {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("⏳ Лимиты, но всё равно продолжить") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Стоп") }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    m: MessageEntity,
    participants: Map<Long, com.yanparker.modelforum.data.db.ParticipantEntity>,
) {
    val isUser = m.role == "user"
    val isJudge = m.role == "judge"
    val p = participants[m.participantId]
    val author = when {
        isUser -> "Вы"
        isJudge -> "Судья"
        else -> p?.name ?: "?"
    }
    val colorIndex = when {
        isUser -> 99
        isJudge -> 100
        else -> p?.colorIndex ?: 0
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isUser) {
                ParticipantAvatar(author.take(1), colorIndex, size = 28)
                Text(
                    author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isJudge) FontWeight.Bold else FontWeight.Normal,
                )
            } else {
                Text("Вы", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                ParticipantAvatar("В", 3, size = 28)
            }
            Text(DateFmt.time(m.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isJudge -> MaterialTheme.colorScheme.tertiaryContainer
                    isUser -> MaterialTheme.colorScheme.secondaryContainer
                    m.status == "failed" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        ) {
            Box(Modifier.padding(12.dp)) {
                if (m.status == "streaming") {
                    Column {
                        TypewriterDots()
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        m.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(if (m.status == "interrupted") 0.6f else 1f),
                    )
                }
            }
        }
        when (m.status) {
            "interrupted" -> Text(
                "⚠️ прервано лимитом — продолжится позже",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF9A825),
            )
            "failed" -> Text(
                "⛔ ошибка",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TypewriterDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "alpha",
    )
    Text("peчатает…", color = MaterialTheme.colorScheme.primary, modifier = Modifier.alpha(alpha), style = MaterialTheme.typography.labelSmall)
}