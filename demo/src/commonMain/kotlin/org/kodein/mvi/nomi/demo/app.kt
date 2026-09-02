package org.kodein.mvi.nomi.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.kodein.mvi.nomi.demo.business.AppModule
import org.kodein.mvi.nomi.demo.business.business
import org.kodein.mvi.nomi.demo.view.EditTask
import org.kodein.mvi.nomi.demo.view.TaskList
import kotlin.uuid.Uuid


private val LocalAppModule = staticCompositionLocalOf<AppModule> { error("No AppModule provided") }
val AppModule.Companion.current @Composable get() = LocalAppModule.current

@Serializable
sealed interface AppScreen : NavKey {
    @Serializable data object TaskList : AppScreen
    @Serializable data object NewTask : AppScreen
    @Serializable data class Task(val id: Uuid) : AppScreen
}

@Composable
private fun rememberMyAppNavBackStack(vararg elements: AppScreen) =
    rememberSerializable(
        serializer = NavBackStack.serializer(AppScreen.serializer())
    ) {
        NavBackStack(*elements)
    }

@Composable
fun App() {
    val appModule = remember { AppModule.business() }
    CompositionLocalProvider(
        LocalAppModule provides appModule
    ) {
        MaterialTheme {
            val backStack = rememberMyAppNavBackStack(AppScreen.TaskList)
            NavDisplay(
                backStack = backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                sceneStrategies = listOf(
                    remember { DialogSceneStrategy() }
                )
            ) { screen ->
                when (screen) {
                    is AppScreen.TaskList -> NavEntry(
                        key = screen,
                        content = {
                            TaskList(
                                goToNewTask = { backStack.add(AppScreen.NewTask) },
                                goToTask = { backStack.add(AppScreen.Task(it)) }
                            )
                        },
                    )
                    is AppScreen.NewTask -> NavEntry(
                        key = screen,
                        metadata = DialogSceneStrategy.dialog(),
                        content = {
                            EditTask(
                                taskId = null,
                                goBack = { backStack.removeLast() }
                            )
                        },
                    )
                    is AppScreen.Task -> NavEntry(
                        key = screen,
                        metadata = DialogSceneStrategy.dialog(),
                        content = {
                            EditTask(
                                taskId = screen.id,
                                goBack = { backStack.removeLast() }
                            )
                        },
                    )
                }
            }
        }
    }
}
