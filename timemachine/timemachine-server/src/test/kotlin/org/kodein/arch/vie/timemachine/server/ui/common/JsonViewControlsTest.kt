package org.kodein.arch.vie.timemachine.server.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class JsonViewControlsTest {

    @Test
    fun `a JSON view starts as a tree with its root open`() {
        assertEquals(JsonViewControls(raw = false, expanded = setOf(emptyList())), JsonViewControls())
    }

    @Test
    fun `toggling a node opens it when closed and closes it when open, and leaves the others`() {
        val opened = JsonViewControls().toggled(listOf("tasks"))
        assertEquals(setOf(emptyList(), listOf("tasks")), opened.expanded)
        assertEquals(setOf(emptyList<String>()), opened.toggled(listOf("tasks")).expanded)
        assertEquals(setOf(listOf("tasks")), opened.toggled(emptyList()).expanded)
    }

    @Test
    fun `toggling a node keeps the view as text or as a tree`() {
        assertTrue(JsonViewControls(raw = true).toggled(listOf("tasks")).raw)
    }
}
