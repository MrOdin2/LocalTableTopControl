package com.tabletopcontrol.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MenuSectionTest {

    @Test
    fun `sections have distinct display orders`() {
        val orders = MenuSection.entries.map { it.displayOrder }
        assertEquals(orders.size, orders.toSet().size, "Each section must have a unique displayOrder")
    }

    @Test
    fun `sections are ordered BASIC APPEARANCE ARRANGE DANGER_ZONE`() {
        val sorted = MenuSection.entries.sortedBy { it.displayOrder }
        assertEquals(
            listOf(MenuSection.BASIC, MenuSection.APPEARANCE, MenuSection.ARRANGE, MenuSection.DANGER_ZONE),
            sorted,
        )
    }

    @Test
    fun `BASIC has the lowest display order`() {
        val min = MenuSection.entries.minBy { it.displayOrder }
        assertEquals(MenuSection.BASIC, min)
    }

    @Test
    fun `DANGER_ZONE has the highest display order`() {
        val max = MenuSection.entries.maxBy { it.displayOrder }
        assertEquals(MenuSection.DANGER_ZONE, max)
    }
}
