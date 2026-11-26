package org.kodein.arch.vie.timemachine.client.type

import kotlin.reflect.KClass


internal actual val KClass<*>.displayName: String? get() = qualifiedName
