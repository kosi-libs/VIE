@file:OptIn(InternalComposeTracingApi::class)

package org.kodein.arch.vie.timemachine.server.fixtures

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import java.util.concurrent.atomic.AtomicInteger


/**
 * How many times each of [functions] ran its body since the last [reset]: a call that skips runs none.
 *
 * The Compose compiler marks the start of each body it runs for the composition tracer, with the
 * function's qualified name, then its file and line in parentheses: this tracer counts the marks of
 * [functions]. A top-level composable is named `<package>.<name>`, a private one as well.
 */
internal class Recompositions(functions: Collection<String>) : CompositionTracer {

    private val counts = functions.associateWith { AtomicInteger() }

    /** How many times [function], one of those counted, ran its body. */
    operator fun get(function: String): Int = counts.getValue(function).get()

    fun reset() = counts.values.forEach { it.set(0) }

    override fun isTraceInProgress(): Boolean = true

    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
        counts[info.substringBefore(" (")]?.incrementAndGet()
    }

    override fun traceEventEnd() = Unit
}

/**
 * [block], with the bodies [functions] run counted. There is one tracer for the whole JVM: this one
 * is set for [block] alone.
 */
internal fun <T> countingRecompositions(vararg functions: String, block: (Recompositions) -> T): T {
    val recompositions = Recompositions(functions.asList())
    Composer.setTracer(recompositions)
    try {
        return block(recompositions)
    } finally {
        Composer.setTracer(null)
    }
}
