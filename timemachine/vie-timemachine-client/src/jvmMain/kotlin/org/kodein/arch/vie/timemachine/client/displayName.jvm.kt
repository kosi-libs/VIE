package org.kodein.arch.vie.timemachine.client

import kotlin.reflect.KClass


internal actual val KClass<*>.displayName: String? get() = qualifiedName
