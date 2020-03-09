package com.android.tools.appinspection.demo.breakout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.android.tools.appinspection.demo.breakout.extensions.set
import com.android.tools.appinspection.demo.breakout.extensions.toAndroidColor
import com.android.tools.appinspection.demo.livestore.breakout.model.game.GameLoop
import com.android.tools.appinspection.demo.livestore.breakout.model.game.GameWorld
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Renderer
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.TextAnchor
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Circle
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Vec
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Color as GameColor
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Rect as GameRect

class GameView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    /**
     * This needs to be set before this view draws for the first time.
     */
    private lateinit var gameLoop: GameLoop
    private lateinit var gameWorld: GameWorld

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTimeNs = System.nanoTime()

    private var isTouchedPrev = false
    private var isTouched = false
    private var lastX = 0f
    private var lastY = 0f

    private val renderer = MyRenderer()

    /**
     * Must be called before this view is drawn for the first time!
     */
    internal fun init(gameLoop: GameLoop, gameWorld: GameWorld) {
        this.gameLoop = gameLoop
        this.gameWorld = gameWorld
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                lastX = event.x
                lastY = event.y
                isTouched = true
            }
            MotionEvent.ACTION_UP -> {
                isTouched = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        run { // Update
            if (isTouched) {
                gameWorld.handlePress(lastX, lastY)
            }
            else {
                if (isTouchedPrev) {
                    gameWorld.handleRelease()
                }
            }
            isTouchedPrev = isTouched

            val currTimeNs = System.nanoTime()
            val elapsedSecs = ((currTimeNs - lastTimeNs).toDouble() / 1_000_000_000.0).toFloat()
            gameLoop.update(elapsedSecs)
            lastTimeNs = currTimeNs
        }

        run { // Render
            renderer.canvas = canvas
            gameWorld.render(renderer)
        }
        postInvalidate()
    }

    private inner class MyRenderer : Renderer {
        lateinit var canvas: Canvas

        override fun translate(delta: Vec) {
            canvas.translate(delta.x, delta.y)
        }

        override fun clearScreen(color: GameColor) {
            paint.style = Paint.Style.FILL
            paint.color = color.toAndroidColor()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        override fun drawRect(rect: GameRect, color: GameColor, thickness: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = thickness
            paint.color = color.toAndroidColor()
            canvas.drawRect(RectF().apply { set(rect) }, paint)
        }

        override fun fillRect(rect: GameRect, color: GameColor) {
            paint.style = Paint.Style.FILL
            paint.color = color.toAndroidColor()
            canvas.drawRect(RectF().apply { set(rect) }, paint)
        }

        override fun drawCircle(circle: Circle, color: GameColor, thickness: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = thickness
            paint.color = color.toAndroidColor()
            canvas.drawCircle(circle.x, circle.y, circle.radius, paint)
        }

        override fun fillCircle(circle: Circle, color: GameColor) {
            paint.style = Paint.Style.FILL
            paint.color = color.toAndroidColor()
            canvas.drawCircle(circle.x, circle.y, circle.radius, paint)
        }

        override fun drawText(text: String, fontSize: Float, color: GameColor, pt: Vec, anchor: TextAnchor) {
            paint.style = Paint.Style.FILL
            paint.textSize = fontSize
            when (anchor) {
                TextAnchor.BOTTOM_LEFT -> {
                    paint.textAlign = Paint.Align.LEFT
                }
                TextAnchor.CENTER -> {
                    paint.textAlign = Paint.Align.CENTER
                }
            }
            paint.color = color.toAndroidColor()
            canvas.drawText(text, pt.x, pt.y, paint)
        }
    }
}