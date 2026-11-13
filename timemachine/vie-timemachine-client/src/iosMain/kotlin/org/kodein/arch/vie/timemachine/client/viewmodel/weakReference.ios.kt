package org.kodein.arch.vie.timemachine.client.viewmodel

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference


@OptIn(ExperimentalNativeApi::class)
internal actual fun <T : Any> weakReference(referent: T): () -> T? {
    val reference = WeakReference(referent)
    return { reference.value }
}
