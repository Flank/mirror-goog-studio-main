package com.android.tools.appinspection.demo.livestore.breakout.model.settings

import com.android.tools.appinspection.demo.livestore.breakout.model.game.GameWorld
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Color
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Colors
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Vec
import com.android.tools.appinspection.livestore.LiveStore
import com.android.tools.appinspection.livestore.ValueEntry
import com.android.tools.appinspection.livestore.protocol.IdeHint

private fun Int.toHex(): String = String.format("%02X", this)
private fun String.toHex(): Int? = toIntOrNull(16)
private fun toString(color: Color): String = color.run { "#${r.toHex()}${g.toHex()}${b.toHex()}" }
private fun fromString(strValue: String): Color? {
    return strValue
        .takeIf { it.length == 7 && strValue[0] == '#' }
        ?.substring(1)
        ?.chunked(2)
        ?.mapNotNull { it.toHex() }
        ?.filter { it in 0 until 256 }
        ?.takeIf { it.size == 3 }
        ?.let { Color(it[0], it[1], it[2]) }
}

private fun LiveStore.addColor(name: String, value: Color): ValueEntry<Color> {
    return addCustom(name, value, { fromString(it) }, { toString(it) }, IdeHint.COLOR)
}

class UiSettings(gameWorld: GameWorld) {
    private val store = LiveStore("UI")

    private val shakeMagnitudeRange = 0f..20f

    val screenMarginTop = store.addFloat("Margin: Top", 50f, 0f .. gameWorld.screenSize.y / 3f)
    val screenMarginSides = store.addFloat("Margin: Sides", 50f, 0f .. gameWorld.screenSize.x / 4f)

    val backgroundColor = store.addColor("Background color", Colors.BLACK)
    val weakBrickColor = store.addColor("Brick color: Weak", Color(0x89, 0xCF, 0xF0))
    val mediumBrickColor = store.addColor("Brick color: Medium", Color(0xCB, 0x41, 0x54))
    val strongBrickColor = store.addColor("Brick color: String", Color(0xD4, 0xAF, 0x37))

    val shakeDampening = store.addFloat("Shake dampening (per second)", 60f)
    val paddleShakeMagnitude = store.addFloat("Shake amount: Paddle", 15f, shakeMagnitudeRange)
    val wallShakeMagnitude = store.addFloat("Shake amount: Wall", 2f, shakeMagnitudeRange)
    val brickHitShakeMagnitude = store.addFloat("Shake amount: Brick (Hit)", 4f, shakeMagnitudeRange)
    val brickBrokenShakeMagnitude = store.addFloat("Shake amount: Brick (Broken)", 10f, shakeMagnitudeRange)
}
