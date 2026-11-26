package org.kodein.arch.vie.timemachine.client.type

import kotlin.reflect.KClass


/** The fully qualified name of this class where the platform supports it, its simple name otherwise. */
internal expect val KClass<*>.displayName: String?
