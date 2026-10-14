package org.kodein.arch.vie.timemachine.client

import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame


class TimeMachineClientRolesTest {

    private fun withClient(block: (TimeMachineClient) -> Unit) {
        val client = TimeMachineClient(TimeMachineConfig(appName = "RolesTest"))
        try {
            block(client)
        } finally {
            client.stop()
        }
    }

    @Test
    fun aRoleIsIntrospectedOncePerType() = withClient { client ->
        val first = client.role<String>(typeOf<String>())
        assertSame(first, client.role<String>(typeOf<String>()))
    }

    @Test
    fun anEquivalentTypeBuiltAnotherTimeSharesTheRole() = withClient { client ->
        val first = client.role<List<String>>(typeOf<List<String>>())
        assertSame(first, client.role<List<String>>(typeOf<List<String>>()))
    }

    @Test
    fun differentTypesGetDifferentRoles() = withClient { client ->
        val strings: VIERole<List<String>> = client.role(typeOf<List<String>>())
        val ints: VIERole<List<Int>> = client.role(typeOf<List<Int>>())
        assertNotSame<VIERole<*>>(strings, ints)
    }
}
