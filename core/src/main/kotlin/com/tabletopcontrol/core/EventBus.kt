package com.tabletopcontrol.core

/**
 * A simple synchronous event bus for inter-module communication.
 *
 * Modules publish events by calling [publish] and subscribe to specific event types
 * by calling [subscribe]. All subscribers of a matching type are notified synchronously
 * in the order they were registered.
 *
 * Plugins must communicate exclusively through this bus and must not hold direct
 * references to other plugins or core singletons.
 */
object EventBus {

    /** Maps each event type to the list of handlers registered for it. */
    @PublishedApi
    internal val handlers = mutableMapOf<Class<*>, MutableList<(Any) -> Unit>>()

    /**
     * Subscribes [handler] to all events of type [T].
     *
     * @param T    the event type to listen for
     * @param handler callback invoked with each published event of type [T]
     */
    inline fun <reified T : Any> subscribe(noinline handler: (T) -> Unit) {
        val list = handlers.getOrPut(T::class.java) { mutableListOf() }
        @Suppress("UNCHECKED_CAST")
        list.add(handler as (Any) -> Unit)
    }

    /**
     * Publishes [event] to all subscribers registered for [event]'s runtime type.
     *
     * @param event the event to broadcast
     */
    fun publish(event: Any) {
        handlers[event::class.java]?.forEach { it(event) }
    }

    /**
     * Removes all subscriptions.
     *
     * Useful in tests to ensure a clean state between test cases.
     */
    fun clear() {
        handlers.clear()
    }
}
