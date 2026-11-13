package org.kodein.arch.vie.timemachine.client.role

import kotlin.reflect.KClass


internal actual val KClass<*>.displayName: String? get() = qualifiedName
