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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

object Routes {
    const val MAIN = "main"
    const val ADD_PARTICIPANT = "participant/add"
    const val EDIT_PARTICIPANT = "participant/edit/{id}"
    fun editParticipant(id: Long) = "participant/edit/$id"
    const val FORUM = "forum/{id}"
    fun forum(id: Long) = "forum/$id"
}

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val settings by container.appSettings.flow.collectAsState(initial = AppSettings())
    ModelForumTheme(darkTheme = settings.darkTheme, dynamicColor = settings.dynamicColor) {
        val nav = rememberNavController()
        Scaffold { inner ->
            NavHost(nav = nav, startDestination = Routes.MAIN, modifier = Modifier.padding(inner)) {
                composable(Routes.MAIN) {
                    MainTabs(nav, container)
                }
                composable(
                    Routes.FORUM,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    ForumScreen(nav, container, entry.arguments?.getLong("id") ?: 0L)
                }
                composable(Routes.ADD_PARTICIPANT) {
                    ParticipantEditorScreen(nav, container, null)
                }
                composable(
                    Routes.EDIT_PARTICIPANT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    ParticipantEditorScreen(nav, container, entry.arguments?.getLong("id"))
                }
            }
        }
    }
}

@Composable
fun MainTabs(nav: androidx.navigation.NavHostController, container: AppContainer) {
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
            0 -> ForumListScreen(nav, container, Modifier.padding(inner))
            1 -> AskScreen(container, Modifier.padding(inner))
            2 -> ParticipantsScreen(nav, container, Modifier.padding(inner))
            else -> SettingsScreen(container, Modifier.padding(inner))
        }
    }
}