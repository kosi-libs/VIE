package org.kodein.arch.vie.timemachine.client.role

import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame


class VIERolesTest {

    private val roles = VIERoles()

    @Test
    fun aRoleIsIntrospectedOncePerType() {
        val first = roles.get<String>(typeOf<String>())
        assertSame(first, roles.get<String>(typeOf<String>()))
    }

    @Test
    fun anEquivalentTypeBuiltAnotherTimeSharesTheRole() {
        val first = roles.get<List<String>>(typeOf<List<String>>())
        assertSame(first, roles.get<List<String>>(typeOf<List<String>>()))
    }

    @Test
    fun differentTypesGetDifferentRoles() {
        val strings: VIERole<List<String>> = roles[typeOf<List<String>>()]
        val ints: VIERole<List<Int>> = roles[typeOf<List<Int>>()]
        assertNotSame<VIERole<*>>(strings, ints)
    }
}
