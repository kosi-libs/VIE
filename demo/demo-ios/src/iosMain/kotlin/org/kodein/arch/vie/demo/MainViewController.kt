package org.kodein.arch.vie.demo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController


fun MainViewController(): UIViewController = ComposeUIViewController { App() }
