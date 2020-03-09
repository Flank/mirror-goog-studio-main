package com.android.tools.appinspection.demo.livestore.breakout.model.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Vec(var x: Float, var y: Float) {
    companion object {
        val EMPTY = Vec()
    }

    constructor(): this(0f, 0f)
    constructor(x: Int, y: Int) : this(x.toFloat(), y.toFloat())
    constructor(other: Vec) : this() { set(other) }

    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun set(other: Vec) {
        this.x = other.x
        this.y = other.y
    }

    fun setByAngle(rad: Float, magnitude: Float) {
        x = cos(rad) * magnitude
        y = sin(rad) * magnitude
    }

    fun reset() {
        x = 0f
        y = 0f
    }

    fun invert() {
        x = -x
        y = -y
    }

    val len2 get() = x * x + y * y
    var len: Float
        get() = sqrt(len2.toDouble()).toFloat()
        set(value) {
            if (len2 > 0) {
                val ratio = value / len
                x *= ratio
                y *= ratio
            }
        }
}