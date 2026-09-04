package org.kodein.arch.vie.demo.view

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.kodein.arch.vie.EmptyVIE
import org.kodein.arch.vie.SignalEffect
import org.kodein.arch.vie.VIEView
import org.kodein.arch.vie.VIEViewScope
import org.kodein.arch.vie.demo.business.AppModule
import org.kodein.arch.vie.demo.current
import org.kodein.arch.vie.demo.data.Task
import org.kodein.arch.vie.demo.vie.TaskListVIE.Intent
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model
import org.kodein.arch.vie.demo.vie.TaskListVIE.Model.Work
import org.kodein.arch.vie.demo.vie.TaskListVIE.Signal
import org.kodein.arch.vie.demo.vie.vm.TaskListVM
import org.kodein.arch.vie.demo.vie.vm.VMFactory
import kotlin.uuid.Uuid


@Composable
fun TaskList(
    goToNewTask: () -> Unit,
    goToTask: (Uuid) -> Unit,
) {
    val appModule = AppModule.current

    VIEView(
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
fun VIEViewScope<Intent, Signal>.TaskList(
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
                title = { Text("Task List") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = goToNewTask) {
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
                            .testTag("loading")
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
                            modifier = Modifier.testTag("row-$id"),
                            leadingContent = {
                                Checkbox(
                                    checked = task.completed,
                                    onCheckedChange = { post(Intent.UpdateTaskCompletion(id, it)) },
                                    enabled = (id to Work.Updating) !in model.work,
                                    modifier = Modifier.testTag("checkbox-$id"),
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { post(Intent.DeleteTask(id)) },
                                    enabled = (id to Work.Deleting) !in model.work,
                                    modifier = Modifier.testTag("delete-$id"),
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

@Preview
@Composable
fun TaskListPreview() {
    EmptyVIE<Intent, Signal>().TaskList(
        model = Model(
            tasks = listOf(
                Uuid.parse("ed7c8378-9cbd-4c20-9d64-425fe531d438") to Task(title = "Task 1", description = "", completed = false),
                Uuid.parse("1ee53463-078c-4bd1-b9dc-db2066b938b3") to Task(title = "Task 2", description = "", completed = true),
                Uuid.parse("a4143d0c-9a2c-49b0-9191-30fbe8356955") to Task(title = "Task 3", description = "", completed = false),
            ),
            work = listOf(
                Uuid.parse("ed7c8378-9cbd-4c20-9d64-425fe531d438") to Work.Updating,
                Uuid.parse("a4143d0c-9a2c-49b0-9191-30fbe8356955") to Work.Deleting,
            ),
        ),
        goToNewTask = {},
        goToTask = {},
    )
}
