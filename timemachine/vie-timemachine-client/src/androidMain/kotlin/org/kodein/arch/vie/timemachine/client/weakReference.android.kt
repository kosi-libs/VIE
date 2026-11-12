package org.kodein.arch.vie.timemachine.client

import java.lang.ref.WeakReference


internal actual fun <T : Any> weakReference(referent: T): () -> T? {
    val reference = WeakReference(referent)
    return { reference.get() }
}
