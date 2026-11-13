package org.kodein.arch.vie.timemachine.client.viewmodel

import java.lang.ref.WeakReference


internal actual fun <T : Any> weakReference(referent: T): () -> T? {
    val reference = WeakReference(referent)
    return { reference.get() }
}
