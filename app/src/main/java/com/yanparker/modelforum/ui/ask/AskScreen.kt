package com.yanparker.modelforum.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanparker.modelforum.data.db.DiscussionEntity
import com.yanparker.modelforum.data.db.MessageEntity
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.ui.Factory
import com.yanparker.modelforum.ui.common.ParticipantAvatar

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AskScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: com.yanparker.modelforum.ui.AskViewModel =
        viewModel(factory = Factory.ask(container))
    val participants by vm.participants.collectAsStateWithLifecycle()
    val currentId by vm.current.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var question by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var judgeId by remember { mutableStateOf(0L) }
    var messages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var discussion by remember { mutableStateOf<DiscussionEntity?>(null) }

    LaunchedEffect(currentId) {
        if (currentId != null) {
            container.discussionRepository.byId(currentId!!).collect { discussion = it }
            container.messageRepository.forDiscussion(currentId!!).collect { messages = it }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Вопрос всем моделям") }) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    question,
                    { question = it },
                    label = { Text("Ваш запрос") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            item {
                Text("Кто отвечает", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    participants.forEach { p ->
                        AssistChip(
                            onClick = { selected = if (p.id in selected) selected - p.id else selected + p.id },
                            label = { Text("${if (p.id in selected) "✓ " else ""}${p.name}") },
                        )
                    }
                }
                if (participants.isEmpty()) {
                    Text("Сначала добавьте участников (ключи) во вкладке «Участники»", color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Text("Судья (анализирует все ответы)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    participants.forEach { p ->
                        AssistChip(
                            onClick = { judgeId = if (judgeId == p.id) 0 else p.id },
                            label = { Text("${if (judgeId == p.id) "⚖ " else ""}${p.name}") },
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { vm.ask(question, selected.toList(), judgeId) },
                    enabled = question.isNotBlank() && selected.isNotEmpty() && judgeId != 0L && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                    Text(if (busy) "Модели думают…" else "Спросить")
                }
            }

            if (discussion != null) {
                item {
                    Text(
                        when (discussion!!.state) {
                            "running" -> "⏳ Модели думают…"
                            "done" -> if (discussion!!.errorNote.isNotBlank()) "⚠️ ${discussion!!.errorNote}" else "✓ Готово"
                            else -> discussion!!.state
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(messages, key = { it.id }) { m ->
                    AnswerCard(m, participants.associate { it.id to it })
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(m: MessageEntity, participants: Map<Long, com.yanparker.modelforum.data.db.ParticipantEntity>) {
    val p = participants[m.participantId]
    val isJudge = m.role == "judge"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isJudge) MaterialTheme.colorScheme.tertiaryContainer
            else if (m.status == "failed") MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isJudge) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                } else {
                    ParticipantAvatar(p?.name?.take(1) ?: "?", p?.colorIndex ?: 0, size = 24)
                }
                Text(
                    if (isJudge) "⚖ Судья: ${p?.name ?: "?"}" else p?.name ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}