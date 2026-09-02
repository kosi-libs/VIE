package org.kodein.mvi.nomi.demo.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.onClick
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
import androidx.compose.ui.unit.dp
import org.kodein.mvi.nomi.MVIViewContext
import org.kodein.mvi.nomi.MviView
import org.kodein.mvi.nomi.SignalEffect
import org.kodein.mvi.nomi.demo.business.AppModule
import org.kodein.mvi.nomi.demo.current
import org.kodein.mvi.nomi.demo.data.Task
import org.kodein.mvi.nomi.demo.mvi.EditTaskMVI.Intent
import org.kodein.mvi.nomi.demo.mvi.EditTaskMVI.Model
import org.kodein.mvi.nomi.demo.mvi.EditTaskMVI.Signal
import org.kodein.mvi.nomi.demo.mvi.vm.EditTaskVM
import org.kodein.mvi.nomi.demo.mvi.vm.VMFactory
import kotlin.uuid.Uuid


@Composable
fun EditTask(
    taskId: Uuid?,
    goBack: () -> Unit,
) {
    val appModule = AppModule.current

    MviView(
        newVM = { VMFactory.EditTaskVM(appModule, taskId) }
    ) {
        EditTask(
            model = it,
            goBack = goBack,
            new = taskId == null
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MVIViewContext<Intent, Signal>.EditTask(
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
                        .fillMaxWidth()
                )
                TextField(
                    state = descriptionState,
                    label = { Text("Description") },
                    modifier = Modifier
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
                        .onClick {}
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(64.dp)
                    )
                }

            }
        }
    }
}