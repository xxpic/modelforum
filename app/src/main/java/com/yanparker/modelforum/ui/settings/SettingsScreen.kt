package com.yanparker.modelforum.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.ui.Factory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: com.yanparker.modelforum.ui.SettingsViewModel =
        viewModel(factory = Factory.settings(container))
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Настройки") }) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Лимиты и запросы", style = MaterialTheme.typography.titleMedium)
            SettingRow("Тёмная тема", {
                Switch(checked = s.darkTheme, onCheckedChange = { vm.update(dark = it) })
            })
            SettingRow("Динамические цвета (Material You)", {
                Switch(checked = s.dynamicColor, onCheckedChange = { vm.update(dynamic = it) })
            })
            SettingRow("Достраивать прерванные лимитом сообщения", {
                Switch(checked = s.resumeInterrupted, onCheckedChange = { vm.update(resume = it) })
            })

            SettingSlider("Интервал между запросами: ${s.minRequestIntervalMs / 1000} с",
                s.minRequestIntervalMs / 1000f, 1f, 12f, { vm.update(intervalMs = (it * 1000).toLong()) })
            SettingSlider("Сообщений на модель: ${s.maxMessagesPerModel}",
                s.maxMessagesPerModel.toFloat(), 3f, 50f, { vm.update(maxMessages = it.toInt()) })
            SettingSlider("Лимит ответа, токенов: ${s.maxTokens}",
                s.maxTokens.toFloat(), 100f, 2000f, { vm.update(maxTokens = it.toInt()) })
            SettingSlider("Температура: %.1f".format(s.temperature),
                s.temperature.toFloat(), 0f, 1.5f, { vm.update(temperature = it.toDouble()) })

            androidx.compose.material3.HorizontalDivider()

            Text("Данные", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { vm.share(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Поделиться историей (TXT)")
            }
            OutlinedButton(onClick = {
                kotlinx.coroutines.MainScope().launch {
                    container.database.clearAllTables()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Очистить все данные (без ключей)")
            }

            Text(
                "Форум ИИ-моделей v1.0\n" +
                    "Ключи хранятся зашифрованными (Android Keystore).\n" +
                    "Бесплатные модели: лимиты снимаются в полночь UTC — обсуждения продолжатся сами.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        control()
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}