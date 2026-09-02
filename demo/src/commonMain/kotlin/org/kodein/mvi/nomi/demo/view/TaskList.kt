package org.kodein.mvi.nomi.demo.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kodein.mvi.nomi.MVIViewContext
import org.kodein.mvi.nomi.MviView
import org.kodein.mvi.nomi.SignalEffect
import org.kodein.mvi.nomi.demo.business.AppModule
import org.kodein.mvi.nomi.demo.current
import org.kodein.mvi.nomi.demo.mvi.EditTaskMVI
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Intent
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Model
import org.kodein.mvi.nomi.demo.mvi.TaskListMVI.Signal
import org.kodein.mvi.nomi.demo.mvi.vm.TaskListVM
import org.kodein.mvi.nomi.demo.mvi.vm.VMFactory
import kotlin.uuid.Uuid


@Composable
fun TaskList(
    goToNewTask: () -> Unit,
    goToTask: (Uuid) -> Unit,
) {
    val appModule = AppModule.current

    MviView(
        newVM = { VMFactory.TaskListVM(appModule) },
    ) {
        TaskList(
            model = it,
            goToNewTask = goToNewTask,
            goToTask = goToTask,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MVIViewContext<Intent, Signal>.TaskList(
    model: Model,
    goToNewTask: () -> Unit,
    goToTask: (Uuid) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    SignalEffect { signal ->
        when (signal) {
            is Signal.Error -> snackbarHostState.showSnackbar(signal.message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task List") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = goToNewTask
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add a task")
            }
        }
    ) { paddingValues ->
        when {
            model.tasks == null -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(64.dp)
                    )
                }
            }
            model.tasks.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Text("No tasks", color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(model.tasks) { (id, task) ->
                        ListItem(
                            onClick = { goToTask(id) },
                            leadingContent = {
                                Checkbox(
                                    checked = task.completed,
                                    onCheckedChange = { post(Intent.UpdateTaskCompletion(id, it)) },
                                    enabled = (id to Model.Work.Updating) !in model.work
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { post(Intent.DeleteTask(id)) },
                                    enabled = (id to Model.Work.Deleting) !in model.work
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            },
                        ) { Text(task.title) }
                    }
                }
            }
        }
    }
}
