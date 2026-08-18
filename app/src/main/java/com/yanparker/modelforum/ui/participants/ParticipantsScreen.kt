package com.yanparker.modelforum.ui.participants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanparker.modelforum.data.db.ParticipantEntity
import com.yanparker.modelforum.data.provider.ProviderPresets
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.ui.Factory
import com.yanparker.modelforum.ui.common.ParticipantAvatar
import com.yanparker.modelforum.ui.common.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsScreen(
    nav: androidx.navigation.NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val vm: com.yanparker.modelforum.ui.ParticipantsViewModel =
        viewModel(factory = Factory.participants(container))
    val participants by vm.participants.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Участники") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(com.yanparker.modelforum.ui.nav.Routes.ADD_PARTICIPANT) }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        }
    ) { inner ->
        if (participants.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Пока нет участников", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Добавьте агрегатор и ключ, чтобы модели могли общаться",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(participants, key = { it.id }) { p ->
                    ParticipantCard(p, vm, nav)
                }
            }
        }
    }
}

@Composable
private fun ParticipantCard(
    p: ParticipantEntity,
    vm: com.yanparker.modelforum.ui.ParticipantsViewModel,
    nav: androidx.navigation.NavHostController,
) {
    val provider = ProviderPresets.byId(p.providerId)
    var showDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (p.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ParticipantAvatar(p.name, p.colorIndex)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${provider.name} · ${p.modelId.take(40)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(status = p.blockReason.ifEmpty { "ok" })
                    Text(
                        when {
                            p.blockReason == "rate" ->
                                "⏳ лимит до ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.blockedUntil))}"
                            p.blockReason == "balance" -> "⛔ баланс/квота, сброс ночью"
                            else -> "Сегодня: ${p.dailyRequests} запросов"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Switch(checked = p.enabled, onCheckedChange = { vm.toggleEnabled(p) })
            IconButton(onClick = { nav.navigate(com.yanparker.modelforum.ui.nav.Routes.editParticipant(p.id)) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Изменить")
            }
            IconButton(onClick = {
                if (showDelete) vm.delete(p) else showDelete = true
            }) {
                Text(if (showDelete) "Точно?" else "🗑", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ProviderLabel(providerId: String) {
    Text(ProviderPresets.byId(providerId).name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}