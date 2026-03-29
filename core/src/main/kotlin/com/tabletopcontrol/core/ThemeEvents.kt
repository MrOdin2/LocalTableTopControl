package com.tabletopcontrol.core

/**
 * Published on [EventBus] whenever the active theme changes.
 *
 * Subscribers can use this event to update any dynamic styling that cannot
 * be driven purely by CSS looked-up colours (e.g. JavaFX Canvas drawing code).
 *
 * @param theme the newly applied [ThemeConfig].
 */
data class ThemeChangedEvent(val theme: ThemeConfig)
