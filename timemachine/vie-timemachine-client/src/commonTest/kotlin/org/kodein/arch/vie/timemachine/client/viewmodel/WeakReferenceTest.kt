package org.kodein.arch.vie.timemachine.client.viewmodel

import kotlin.test.Test
import kotlin.test.assertSame


class WeakReferenceTest {

    @Test
    fun aWeakReferenceGivesItsReferentBackWhileSomethingElseHoldsIt() {
        val referent = Any()
        val reference = weakReference(referent)
        assertSame(referent, reference())
    }
}
