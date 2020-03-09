package com.android.tools.appinspection.demo.livestore.breakout.model.game

import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Color
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Circle
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Rect
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Vec
import com.android.tools.appinspection.demo.livestore.breakout.model.settings.UiSettings

sealed class Collidable {
    abstract val shape: Rect
}

class Brick(type: Type, private var ui: UiSettings): Collidable() {
    enum class Type {
        WEAK,
        MEDIUM,
        STRONG,
    }

    override val shape = Rect()
    private var hitsLeft = type.ordinal + 1

    fun hit(): Boolean {
        --hitsLeft
        return hitsLeft <= 0
    }

    fun toColor(): Color {
        return when {
            hitsLeft >= 3 -> ui.strongBrickColor.value
            hitsLeft == 2 -> ui.mediumBrickColor.value
            else -> ui.weakBrickColor.value
        }
    }
}

class Paddle: Collidable() {
    override val shape = Rect()
}

class Ball {
    val shape = Circle()
    val vel = Vec()
}