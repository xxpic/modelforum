package com.yanparker.modelforum.ui.forum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanparker.modelforum.data.db.DiscussionEntity
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.ui.Factory
import com.yanparker.modelforum.ui.common.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumListScreen(
    container: AppContainer,
    onOpenForum: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: com.yanparker.modelforum.ui.ForumListViewModel =
        viewModel(factory = Factory.forumList(container))
    val discussions by vm.discussions.collectAsStateWithLifecycle()
    val participants by vm.participants.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Дискуссии") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Новая дискуссия")
            }
        },
    ) { inner ->
        if (discussions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Пока нет дискуссий", style = MaterialTheme.typography.titleMedium)
                    Text("Нажмите + чтобы начать обсуждение темы моделями", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(12.dp)) {
                items(discussions, key = { it.id }) { d ->
                    DiscussionCard(d, vm) { onOpenForum(d.id) }
                }
            }
        }
    }

    if (showNew) {
        NewDiscussionDialog(
            participants = participants,
            onConfirm = { title, ids, maxMsgs, maxTokens, temp ->
                vm.create(title, ids, maxMsgs, maxTokens, temp)
                showNew = false
            },
            onDismiss = { showNew = false },
        )
    }
}

@Composable
private fun DiscussionCard(
    d: DiscussionEntity,
    vm: com.yanparker.modelforum.ui.ForumListViewModel,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        onClick = { onOpen() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(status = if (d.state == "running") "running" else d.state)
                Text(d.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    when (d.state) {
                        "running" -> "идёт"
                        "paused" -> "пауза"
                        "waiting_limits" -> "⏳ лимиты"
                        "done" -> "завершена"
                        "stopped" -> "остановлена"
                        else -> "готова"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (d.mode == "ask") "Вопрос · " else "Форум · " +
                        java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(d.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                if (d.state == "running" || d.state == "waiting_limits" || d.state == "paused") {
                    TextButton(onClick = { vm.toggle(d) }) {
                        Text(if (d.state == "running") "⏸ Пауза" else "▶ Продолжить")
                    }
                    TextButton(onClick = { vm.stop(d) }) { Text("⏹ Стоп") }
                }
                IconButton(onClick = { vm.delete(d) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NewDiscussionDialog(
    participants: List<com.yanparker.modelforum.data.db.ParticipantEntity>,
    onConfirm: (String, List<Long>, Int, Int, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var maxMsgs by remember { mutableIntStateOf(15) }
    var maxTokens by remember { mutableIntStateOf(800) }
    var temp by remember { mutableStateOf(0.7) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая дискуссия") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Тема обсуждения") }, modifier = Modifier.fillMaxWidth())
                Text("Участники", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    participants.forEach { p ->
                        AssistChip(
                            onClick = {
                                selected = if (p.id in selected) selected - p.id else selected + p.id
                            },
                            label = { Text(p.name) },
                            leadingIcon = if (p.id in selected) null else null,
                        )
                    }
                }
                if (participants.isEmpty()) {
                    Text("Добавьте участников во вкладке «Участники»", color = MaterialTheme.colorScheme.error)
                }
                Text("Сообщений на модель: $maxMsgs", style = MaterialTheme.typography.bodySmall)
                Slider(value = maxMsgs.toFloat(), onValueChange = { maxMsgs = it.toInt() }, valueRange = 3f..50f)
                Text("Длина ответа, токенов: $maxTokens", style = MaterialTheme.typography.bodySmall)
                Slider(value = maxTokens.toFloat(), onValueChange = { maxTokens = it.toInt() }, valueRange = 100f..2000f)
                Text("Температура: %.1f".format(temp), style = MaterialTheme.typography.bodySmall)
                Slider(value = temp.toFloat(), onValueChange = { temp = it.toDouble() }, valueRange = 0f..1.5f)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, selected.toList(), maxMsgs, maxTokens, temp) },
                enabled = title.isNotBlank() && selected.isNotEmpty(),
            ) { Text("Создать и запустить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}