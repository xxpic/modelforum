package com.yanparker.modelforum.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.yanparker.modelforum.App
import com.yanparker.modelforum.data.prefs.AppSettings
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.ui.ask.AskScreen
import com.yanparker.modelforum.ui.forum.ForumListScreen
import com.yanparker.modelforum.ui.forum.ForumScreen
import com.yanparker.modelforum.ui.participants.ParticipantEditorScreen
import com.yanparker.modelforum.ui.participants.ParticipantsScreen
import com.yanparker.modelforum.ui.settings.SettingsScreen
import com.yanparker.modelforum.ui.theme.ModelForumTheme

sealed class Screen {
    object Main : Screen()
    data class OpenForum(val id: Long) : Screen()
    data class EditParticipant(val id: Long?) : Screen()
}

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val settings by container.appSettings.flow.collectAsState(initial = AppSettings())

    ModelForumTheme(darkTheme = settings.darkTheme, dynamicColor = settings.dynamicColor) {
        var screen by rememberSaveable { mutableStateOf<Screen>(Screen.Main) }
        when (val s = screen) {
            is Screen.Main -> {
                MainTabs(
                    container = container,
                    onOpenForum = { id -> screen = Screen.OpenForum(id) },
                    onAddParticipant = { screen = Screen.EditParticipant(null) },
                    onEditParticipant = { id -> screen = Screen.EditParticipant(id) },
                )
            }
            is Screen.OpenForum -> {
                ForumScreen(
                    container = container,
                    discussionId = s.id,
                    onBack = { screen = Screen.Main },
                    modifier = Modifier,
                )
            }
            is Screen.EditParticipant -> {
                ParticipantEditorScreen(
                    container = container,
                    editId = s.id,
                    onBack = { screen = Screen.Main },
                )
            }
        }
    }
}

@Composable
fun MainTabs(
    container: AppContainer,
    onOpenForum: (Long) -> Unit,
    onAddParticipant: () -> Unit,
    onEditParticipant: (Long) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    Pair(Icons.Filled.Forum, "Дискуссии"),
                    Pair(Icons.Filled.QuestionAnswer, "Вопрос"),
                    Pair(Icons.Filled.Groups, "Участники"),
                    Pair(Icons.Filled.Settings, "Настройки"),
                )
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { inner ->
        when (tab) {
            0 -> ForumListScreen(container, onOpenForum, Modifier.padding(inner))
            1 -> AskScreen(container, Modifier.padding(inner))
            2 -> ParticipantsScreen(container, onAddParticipant, onEditParticipant, Modifier.padding(inner))
            else -> SettingsScreen(container, Modifier.padding(inner))
        }
    }
}