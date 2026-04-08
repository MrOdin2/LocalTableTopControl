package com.tabletopcontrol.core.ui.dialog

import javafx.scene.control.ButtonType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogFlowsTest {
    @Test
    fun `resultForButton returns null for cancel button`() {
        val result = DialogFlows.resultForButton(ButtonType.CANCEL) { "accepted" }
        assertNull(result)
    }

    @Test
    fun `resultForButton returns value for confirm button`() {
        val result = DialogFlows.resultForButton(ButtonType.OK) { "accepted" }
        assertEquals("accepted", result)
    }

    @Test
    fun `confirmation tracker stays unconfirmed on failed validation`() {
        val tracker = DialogFlows.ConfirmationTracker()
        val accepted = tracker.attemptConfirm { false }

        assertFalse(accepted)
        assertFalse(tracker.confirmed)
    }

    @Test
    fun `confirmation tracker marks confirmed on successful validation`() {
        val tracker = DialogFlows.ConfirmationTracker()
        val accepted = tracker.attemptConfirm { true }

        assertTrue(accepted)
        assertTrue(tracker.confirmed)
    }
}
