package org.kodein.arch.vie.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.kodein.arch.vie.demo.view.EditTask
import org.kodein.arch.vie.demo.vie.vm.DemoViewModels
import platform.UIKit.UIViewController
import kotlin.uuid.Uuid


/**
 * The Compose task editor, for SwiftUI to present: edits the task [taskId], or a new one when it is
 * `null`, and calls [goBack] once done. The task list is a SwiftUI view on the same
 * [DemoViewModels.appModule], so an edit made here shows in the list.
 */
@Suppress("FunctionName")
fun EditTaskViewController(taskId: Uuid?, goBack: () -> Unit): UIViewController =
    ComposeUIViewController {
        // The ViewModelStore that ComposeUIViewController provides is not cleared when SwiftUI
        // dismisses the controller, even though the composition is disposed (Compose
        // Multiplatform 1.12.0): the editor's ViewModel would outlive its sheet. A store of its
        // own, cleared with the composition, ends it with the sheet.
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) {
            onDispose { owner.viewModelStore.clear() }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            MaterialTheme {
                ProvideAppModule(DemoViewModels.appModule) {
                    EditTask(taskId = taskId, goBack = goBack)
                }
            }
        }
    }
