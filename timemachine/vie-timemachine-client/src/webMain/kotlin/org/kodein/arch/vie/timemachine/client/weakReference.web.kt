@file:OptIn(ExperimentalWasmJsInterop::class)

package org.kodein.arch.vie.timemachine.client

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsReference
import kotlin.js.toJsReference
import kotlin.js.unsafeCast


/** JavaScript's own weak reference. */
private external class WeakRef(target: JsAny) : JsAny {
    fun deref(): JsAny?
}

internal actual fun <T : Any> weakReference(referent: T): () -> T? {
    val reference = WeakRef(referent.toJsReference())
    return { reference.deref()?.unsafeCast<JsReference<T>>()?.get() }
}
