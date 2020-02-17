package com.android.tools.appinspection.demo.livestore.breakout.model.math

class Circle(var x: Float, var y: Float, var radius: Float) {
    constructor() : this(0f, 0f, 0f)
    constructor(center: Vec, radius: Float) : this(center.x, center.y, radius)

    val center: Vec get() = Vec(x, y)

    var left: Float
        get() = x - radius
        set(value) { x = value + radius }

    var right: Float
        get() = x + radius
        set(value) { x = value - radius }

    var top: Float
        get() = y - radius
        set(value) { y = value + radius }

    var bottom: Float
        get() = y + radius
        set(value) { y = value - radius }


    fun intersectsWith(other: Circle): Boolean {
        val distance = Vec(other.x - x, other.y - y)
        val disjoint = distance.len2 > (radius * radius)
        return !disjoint
    }

    fun toBoundingRect() = Rect(left, top, radius * 2f, radius * 2f)

    fun reset() {
        x = 0f
        y = 0f
        radius = 0f
    }

    fun set(center: Vec, radius: Float) {
        x = center.x
        y = center.y
        this.radius = radius
    }

    fun set(other: Circle) {
        x = other.x
        y = other.y
        radius = other.radius
    }
}