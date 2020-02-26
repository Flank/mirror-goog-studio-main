package com.android.tools.appinspection.demo.livestore.breakout.model.settings

import com.android.tools.appinspection.demo.livestore.breakout.model.game.GameWorld
import com.android.tools.appinspection.livestore.LiveStore

class GameplaySettings(world: GameWorld) {
    private val store = LiveStore("Gameplay")

    val brickPattern = store.addEnum("Brick pattern", GameWorld.BrickPattern.RANDOM)
    val ballSpeed = store.addFloat("Ball speed (pixels / second)", 1000f, 1f..3000f)
    val paddleWidth = store.addFloat("Paddle width", world.screenSize.x / 5f, 100f..400f)
    val paddleHeight = store.addFloat("Paddle height", world.screenSize.y / 50f, 20f..60f)
    val ballRadius = store.addFloat("Ball radius", world.screenSize.x / 45f, 20f..50f)
}