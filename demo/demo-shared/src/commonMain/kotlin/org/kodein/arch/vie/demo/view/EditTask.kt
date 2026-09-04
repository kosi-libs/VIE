package org.kodein.arch.vie.demo.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Intent
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Model
import org.kodein.arch.vie.demo.vie.EditTaskVIE.Signal
import org.kodein.arch.vie.demo.vie.vm.EditTaskVM
import org.kodein.arch.vie.demo.vie.vm.VMFactory
import kotlin.uuid.Uuid


@Composable
fun EditTask(
    taskId: Uuid?,
    goBack: () -> Unit,
) {
    val appModule = AppModule.current

    VIEView(
        newVM = { VMFactory.EditTaskVM(appModule, taskId) }
    ) {
        EditTask(
            model = it,
            goBack = goBack,
            new = taskId == null
        )
    }
}

@Composable
fun VIEViewScope<Intent, Signal>.EditTask(
    model: Model,
    goBack: () -> Unit,
    new: Boolean,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    SignalEffect { signal ->
        when (signal) {
            is Signal.Edited -> goBack()
            is Signal.Error -> snackbarHostState.showSnackbar(signal.message)
            Signal.LoadFail -> goBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Task") },
                navigationIcon = {
                    IconButton(
                        onClick = goBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .heightIn(max = 400.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val titleState = rememberTextFieldState()
            val descriptionState = rememberTextFieldState()

            LaunchedEffect(model.task) {
                if (model.task != null) {
                    titleState.setTextAndPlaceCursorAtEnd(model.task.title)
                    descriptionState.setTextAndPlaceCursorAtEnd(model.task.description)
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                TextField(
                    state = titleState,
                    label = { Text("Title") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .testTag("title")
                        .fillMaxWidth()
                )
                TextField(
                    state = descriptionState,
                    label = { Text("Description") },
                    modifier = Modifier
                        .testTag("description")
                        .weight(1f)
                        .fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            post(
                                Intent.Edit(
                                    Task(
                                        title = titleState.text.toString(),
                                        description = descriptionState.text.toString(),
                                        completed = false,
                                    )
                                )
                            )
                        }
                    ) {
                        Text(if (new) "Create" else "Update")
                    }
                }
            }

            if (model.loading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* dismiss */ },
                        )
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .testTag("loading")
                            .size(64.dp)
                    )
                }

            }
        }
    }
}

@Preview
@Composable
fun EditTaskPreview() {
    EmptyVIE<Intent, Signal>().EditTask(
        model = Model(
            loading = false,
            task = Task(title = "Task 1", description = "", completed = false),
        ),
        goBack = {},
        new = false,
    )
}
