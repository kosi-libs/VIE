package org.kodein.arch.vie.timemachine.client


/**
 * Holds [referent] without keeping it from being garbage-collected: the returned function gives it
 * back, or `null` once it was collected.
 */
internal expect fun <T : Any> weakReference(referent: T): () -> T?
