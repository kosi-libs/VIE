package org.kodein.arch.vie.timemachine.server.fixtures

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import java.awt.datatransfer.DataFlavor


/** A clipboard that keeps the text it is given, in place of the system's. */
@OptIn(ExperimentalComposeUiApi::class)
internal class KeptClipboard : Clipboard {

    var text: String? = null
        private set

    override suspend fun getClipEntry(): ClipEntry? = null

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        text = clipEntry?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as String?
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override val nativeClipboard: Any get() = this
}
