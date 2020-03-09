package com.android.tools.appinspection.demo.livestore.breakout.model.math

class Rect(var x: Float, var y: Float, var w: Float, var h: Float) {
    constructor() : this(0f, 0f, 0f, 0f)
    constructor(pos: Vec, size: Vec) : this(pos.x, pos.y, size.x, size.y)

    var centerX: Float
        get() = x + w / 2f
        set(value) { x = value - w / 2f}

    var centerY: Float
        get() = y + h / 2f
        set(value) { y = value - h / 2f}

    val center: Vec get() = Vec(centerX, centerY)

    var right: Float
        get() = x + w
        set(value) { x = value - w }

    var bottom: Float
        get() = y + h
        set(value) { y = value - h }

    fun intersectsWith(other: Rect): Boolean {
        val disjoint = right < other.x || bottom < other.y || x > other.right || y > other.bottom
        return !disjoint
    }

    fun reset() {
        x = 0f
        y = 0f
        w = 0f
        h = 0f
    }

    fun set(pos: Vec, size: Vec) {
        x = pos.x
        y = pos.y
        w = size.x
        h = size.y
    }

    fun set(x: Float, y: Float, w: Float, h: Float) {
        this.x = x
        this.y = y
        this.w = w
        this.h = h
    }

    fun set(other: Rect) {
        x = other.x
        y = other.y
        w = other.w
        h = other.h
    }
}