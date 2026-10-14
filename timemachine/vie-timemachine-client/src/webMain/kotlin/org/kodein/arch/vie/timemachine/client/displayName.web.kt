package org.kodein.arch.vie.timemachine.client

import kotlin.reflect.KClass


// KClass.qualifiedName is unsupported on js.
internal actual val KClass<*>.displayName: String? get() = simpleName
