package com.android.tools.appinspection.demo.livestore.breakout.model.game

import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Colors
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Renderer
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Vec

class GameWorld(
    private val screenSize: Vec) {

    private enum class State {
        STARTING,
        PLAYING,
        DEAD,
        WON,
    }

    fun handlePress(x: Float, y: Float) {

    }

    fun handleRelease() {

    }

    fun update(elapsedSecs: Float) {

    }

    fun render(renderer: Renderer) {
        renderer.clearScreen(Colors.BLACK)
    }
}
