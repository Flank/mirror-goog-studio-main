package com.android.tools.appinspection.demo.livestore.breakout.model.game

import kotlin.math.min

private const val TIME_STEP_SECS: Float = 0.0167f // ~16ms, aiming for 60fps

/**
 * Logic for keeping track of main game loop timing. Call [update] with time elapsed, and then
 * implementations should override [handleFrame] to update / render a frame.
 */
abstract class GameLoop
{
    private var elapsedTotalSecs = 0f

    abstract fun handleFrame(elapsedSecs: Float)

    /**
     * Any time not consumed by the last update
     *
     * This class updates in time steps - so if 35 ms passed, we'd call update
     * 2 times (with a timestep of 16.67ms), with a handful of milliseconds
     * remaining.
     */
    private var remainingTimeSecs: Float = 0f
    private var framesCounter = 0

    fun update(elapsedSecs: Float) {
        // Cap elapsed (so, e.g., if we hit a breakpoint or major lag, the simulation doesn't
        // go crazy on resume)
        @Suppress("NAME_SHADOWING")
        var elapsedSecs = min(elapsedSecs, 0.5f) + remainingTimeSecs

        framesCounter++
        while (elapsedSecs >= TIME_STEP_SECS) {
            elapsedSecs -= TIME_STEP_SECS
            elapsedTotalSecs += TIME_STEP_SECS
            handleFrame(TIME_STEP_SECS)
        }

        remainingTimeSecs = elapsedSecs
    }
}