package com.yanparker.modelforum.ui.participants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanparker.modelforum.data.key.ApiKeyField
import com.yanparker.modelforum.data.provider.FreeModelFilter
import com.yanparker.modelforum.data.provider.ProviderPresets
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.ui.Factory
import com.yanparker.modelforum.ui.theme.ParticipantColors
import com.yanparker.modelforum.ui.theme.participantColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantEditorScreen(
    nav: androidx.navigation.NavHostController,
    container: AppContainer,
    editId: Long?,
) {
    val vm: com.yanparker.modelforum.ui.ParticipantsViewModel =
        viewModel(factory = Factory.participants(container))

    var providerId by remember { mutableStateOf("openrouter") }
    var key by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }

    var customBase by remember { mutableStateOf("") }
    var customChat by remember { mutableStateOf("/chat/completions") }
    var customModels by remember { mutableStateOf("/models") }

    LaunchedEffect(editId) {
        if (editId != null) {
            val p = try { container.participantRepository.allOnce().firstOrNull { it.id == editId } } catch (e: Exception) { null }
            if (p != null) {
                providerId = p.providerId
                modelId = p.modelId
                name = p.name
                colorIndex = p.colorIndex
                customBase = p.customBaseUrl
                customChat = p.customChatPath.ifBlank { "/chat/completions" }
                customModels = p.customModelsPath.ifBlank { "/models" }
            }
        }
    }

    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var onlyFree by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }

    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val preset = ProviderPresets.byId(providerId)

    LaunchedEffect(editId, providerId) {
        if (editId == null) {
            models = emptyList()
            modelId = ""
        }
    }

    fun modelsPreset() = container.providerClient.presetFor(
        providerId, customBase, customChat, customModels
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "Новый участник" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Агрегатор", style = MaterialTheme.typography.titleSmall)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = preset.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Провайдер") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ProviderPresets.all.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    providerId = p.id
                                    expanded = false
                                    modelId = ""
                                    models = emptyList()
                                },
                            )
                        }
                    }
                }
            }

            if (providerId == "custom") {
                item { OutlinedTextField(customBase, { customBase = it }, label = { Text("Base URL (обязательно)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(customChat, { customChat = it }, label = { Text("Путь чата") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(customModels, { customModels = it }, label = { Text("Путь списка моделей") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            }

            item {
                ApiKeyField(value = key, onChange = { key = it; modelsError = null },
                    label = if (editId != null) "Новый ключ (пусто = оставить старый)" else "API-ключ")
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        loadingModels = true
                        modelsError = null
                        scope.launch {
                            try {
                                val all = container.providerClient.fetchModels(key, modelsPreset())
                                models = all.filter { FreeModelFilter.isTextCapable(it) }
                                    .sortedWith(compareByDescending<String> { FreeModelFilter.isFree(providerId, it) }
                                        .thenBy { it })
                                if (models.isEmpty()) modelsError = "Модели не найдены. Проверьте ключ."
                            } catch (e: Exception) {
                                modelsError = "Не удалось загрузить: ${e.message}"
                            } finally {
                                loadingModels = false
                            }
                        }
                    }) {
                        if (loadingModels) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Загрузить модели")
                    }
                    TextButton(onClick = { onlyFree = !onlyFree }) {
                        Text(if (onlyFree) "Только бесплатные: вкл" else "Только бесплатные: выкл")
                    }
                }
            }

            if (modelsError != null) {
                item { Text("⚠️ $modelsError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(modelId, { modelId = it }, label = { Text("Модель") },
                        modifier = Modifier.weight(1f), singleLine = true)
                }
                Text(if (modelId.isNotBlank() && FreeModelFilter.isFree(providerId, modelId)) "✓ бесплатная модель"
                    else "Модель будет использоваться как бесплатная, если тариф позволяет",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            if (models.isNotEmpty()) {
                item {
                    Text("Список моделей", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(search, { search = it }, label = { Text("Поиск") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                items(models.filter {
                    FreeModelFilter.isFree(providerId, it) || !onlyFree
                }.filter { it.contains(search, ignoreCase = true) || search.isBlank() }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { modelId = m }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            Text(m, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (m == modelId) FontWeight.Bold else FontWeight.Normal)
                        }
                        if (FreeModelFilter.isFree(providerId, m)) {
                            Text("бесплатно", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                Text("Ник в форуме", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(name, { name = it }, label = { Text("Например: Qwen-3.5") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            item {
                Text("Цвет аватара", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParticipantColors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (i == colorIndex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { colorIndex = i }
                        )
                    }
                }
            }

            item {
                OutlinedButton(onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        try {
                            val resp = container.providerClient.chat(
                                key,
                                modelsPreset(),
                                com.yanparker.modelforum.data.network.ChatRequest(
                                    model = modelId,
                                    messages = listOf(com.yanparker.modelforum.data.network.ChatMessage("user", "Ответь одним словом: привет")),
                                    maxTokens = 10,
                                ),
                            )
                            testResult = "✓ Ответ: ${resp.choices.firstOrNull()?.message?.content?.trim()?.take(80)}"
                        } catch (e: Exception) {
                            testResult = "✗ ${e.message}"
                        } finally {
                            testing = false
                        }
                    }
                }) {
                    if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Проверить соединение")
                }
            }

            if (testResult != null) {
                item { Text(testResult!!, style = MaterialTheme.typography.bodySmall) }
            }

            item {
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            if (editId == null) {
                                vm.add(providerId, name.ifBlank { modelId }, modelId, key, colorIndex,
                                    if (providerId == "custom") customBase else "",
                                    if (providerId == "custom") customChat else "",
                                    if (providerId == "custom") customModels else "")
                            } else {
                                vm.update(editId, name.ifBlank { modelId }, modelId, colorIndex,
                                    if (providerId == "custom") customBase else "",
                                    if (providerId == "custom") customChat else "",
                                    if (providerId == "custom") customModels else "")
                                if (key.isNotBlank()) vm.updateKey(editId, key)
                            }
                            nav.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (editId == null) "Сохранить участника" else "Сохранить изменения")
                }
            }
        }
    }
}