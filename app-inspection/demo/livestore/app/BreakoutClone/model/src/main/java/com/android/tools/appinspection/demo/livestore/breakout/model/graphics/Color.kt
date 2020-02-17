package com.android.tools.appinspection.demo.livestore.breakout.model.graphics

class Color(var a: Int, var r: Int, var g: Int, var b: Int) {
    constructor(r: Int, g: Int, b: Int) : this(255, r, g, b)
    constructor() : this(0, 0, 0)
    constructor(other: Color) : this(other.a, other.r, other.g, other.b)

    fun reset() {
        a = 255
        r = 0
        g = 0
        b = 0
    }

    fun set(a: Int, r: Int, g: Int, b: Int) {
        this.a = a
        this.r = r
        this.g = g
        this.b = b
    }

    fun set(r: Int, g: Int, b: Int) = set(255, r, g, b)
}

object Colors {
    val BLACK = Color()
    val WHITE = Color(255, 255, 255)

    val RED = Color(255, 0, 0)
    val GREEN = Color(0, 255, 0)
    val BLUE = Color(0, 0, 255)

    val MAGENTA = Color(255, 0, 255)
    val YELLOW = Color(255, 255, 0)
}