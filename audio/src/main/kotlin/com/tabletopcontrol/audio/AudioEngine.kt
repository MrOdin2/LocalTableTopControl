package com.tabletopcontrol.audio

/**
 * Three-layer audio engine for the tabletop application.
 *
 * Manages three independent playback layers:
 * - [Layer.MUSIC]   — long, looping background music tracks.
 * - [Layer.AMBIENT] — looping ambient environment sounds.
 * - [Layer.SFX]     — one-shot sound effects (target latency < 200 ms).
 *
 * Volume for each layer is controlled independently via [setVolume].
 */
class AudioEngine {

    /** The three independent audio layers. */
    enum class Layer { MUSIC, AMBIENT, SFX }

    /** Current volume for each layer, in the range 0.0–1.0. */
    private val volumes = mutableMapOf(
        Layer.MUSIC to 1.0,
        Layer.AMBIENT to 1.0,
        Layer.SFX to 1.0,
    )

    /**
     * Plays the audio resource at [resourcePath] on the given [layer].
     *
     * @param layer        the target audio layer
     * @param resourcePath URL or classpath resource path to the audio file
     * @param loop         `true` to loop the track automatically; `false` for one-shot
     */
    fun play(layer: Layer, resourcePath: String, loop: Boolean = false) {
        // TODO: implement with JavaFX MediaPlayer
    }

    /**
     * Stops playback on [layer].
     *
     * @param layer the layer to stop
     */
    fun stop(layer: Layer) {
        // TODO: implement
    }

    /**
     * Sets the volume for [layer].
     *
     * @param layer  the layer to adjust
     * @param volume a value in the range `0.0` (silent) to `1.0` (full volume)
     * @throws IllegalArgumentException if [volume] is outside `0.0..1.0`
     */
    fun setVolume(layer: Layer, volume: Double) {
        require(volume in 0.0..1.0) { "Volume must be between 0.0 and 1.0, was $volume" }
        volumes[layer] = volume
        // TODO: propagate to the underlying MediaPlayer
    }

    /**
     * Returns the current volume for [layer].
     *
     * @param layer the layer to query
     * @return the volume in the range `0.0..1.0`
     */
    fun getVolume(layer: Layer): Double = volumes.getValue(layer)
}
