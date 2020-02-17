package com.android.tools.appinspection.demo.livestore.breakout.model.graphics

import com.android.tools.appinspection.demo.livestore.breakout.model.math.Circle
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Rect
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Vec

enum class TextAnchor {
    BOTTOM_LEFT,
    CENTER
}

interface Renderer {
    fun translate(delta: Vec)
    fun clearScreen(color: Color)
    fun drawRect(rect: Rect, color: Color, thickness: Float = 1f)
    fun fillRect(rect: Rect, color: Color)
    fun drawCircle(circle: Circle, color: Color, thickness: Float = 1f)
    fun fillCircle(circle: Circle, color: Color)
    fun drawText(text: String, fontSize: Float, color: Color, pt: Vec, anchor: TextAnchor = TextAnchor.CENTER)
}

inline fun Renderer.withTranslate(delta: Vec, block: () -> Unit) {
    translate(delta)
    block()
    delta.invert()
    translate(delta)
    delta.invert()
}