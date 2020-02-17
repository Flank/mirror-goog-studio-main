package com.android.tools.appinspection.demo.breakout.extensions

import android.graphics.RectF
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Color
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Rect
import android.graphics.Color as AndroidColor

// Misc. extension / utility methods for working with the underlying
// game engine layer.

fun RectF.set(rect: Rect) { set(rect.x, rect.y, rect.right, rect.bottom) }
fun Color.toAndroidColor() = AndroidColor.argb(a, r, g, b)