package org.kodein.arch.vie.timemachine.client.type

import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.kodein.arch.vie.timemachine.client.fixtures.testLog


class VIEObjectTypesTest {

    private val types = VIEObjectTypes(testLog)

    @Test
    fun aTypeIsIntrospectedOnce() {
        val first = types.get<String>(typeOf<String>())
        assertSame(first, types.get<String>(typeOf<String>()))
    }

    @Test
    fun anEquivalentTypeBuiltAnotherTimeIsNotIntrospectedAgain() {
        val first = types.get<List<String>>(typeOf<List<String>>())
        assertSame(first, types.get<List<String>>(typeOf<List<String>>()))
    }

    @Test
    fun differentTypesAreIntrospectedApart() {
        val strings: VIEObjectType<List<String>> = types[typeOf<List<String>>()]
        val ints: VIEObjectType<List<Int>> = types[typeOf<List<Int>>()]
        assertNotSame<VIEObjectType<*>>(strings, ints)
    }
}
