package org.kodein.arch.vie

import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Base for tests that build a [VIEViewModel], whose `viewModelScope` needs a test `Dispatchers.Main`. */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class VIETest {

    @BeforeTest
    fun before() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun after() {
        Dispatchers.resetMain()
    }

    /** Compose [Host] test that is closed once [block] ends. */
    protected fun hostedTest(
        extras: CreationExtras = CreationExtras.Empty,
        block: suspend TestScope.(Host) -> Unit,
    ): TestResult = runTest {
        val host = Host(this, extras)
        try {
            block(host)
        } finally {
            host.close()
        }
    }
}
