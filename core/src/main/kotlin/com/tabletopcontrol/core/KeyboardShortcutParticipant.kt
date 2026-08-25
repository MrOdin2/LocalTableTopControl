package com.tabletopcontrol.core

import javafx.scene.input.KeyEvent

/** A plugin that handles application-wide keyboard shortcuts on the DM scene. */
interface KeyboardShortcutParticipant {
    /** Returns true when [event] matched and was handled. */
    fun handleKeyPressed(event: KeyEvent): Boolean

    /** Notifies the participant that a key was released so held keys are not retriggered. */
    fun handleKeyReleased(event: KeyEvent) {}
}
