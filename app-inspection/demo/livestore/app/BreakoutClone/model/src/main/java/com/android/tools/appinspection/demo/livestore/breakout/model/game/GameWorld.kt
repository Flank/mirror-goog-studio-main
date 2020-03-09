package com.android.tools.appinspection.demo.livestore.breakout.model.game

import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Colors
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.Renderer
import com.android.tools.appinspection.demo.livestore.breakout.model.graphics.withTranslate
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Rect
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Vec
import com.android.tools.appinspection.demo.livestore.breakout.model.settings.DebugSettings
import com.android.tools.appinspection.demo.livestore.breakout.model.settings.GameplaySettings
import com.android.tools.appinspection.demo.livestore.breakout.model.settings.UiSettings
import kotlin.math.*
import kotlin.random.Random

private const val MAX_SHAKE = 60f
class GameWorld(val screenSize: Vec) {

    enum class BrickPattern {
        RANDOM,
        EASY,
        GRADIENT,
        LINES,
    }

    private val ui = UiSettings(this).apply {
        screenMarginTop.addListener { updatePaddle() }
    }
    private val gameplay = GameplaySettings(this).apply {
        ballRadius.addListener { updateBall() }
        ballSpeed.addListener { updateBall() }

        paddleWidth.addListener { updatePaddle() }
        paddleHeight.addListener { updatePaddle() }

        brickPattern.addListener { updateBrickPattern() }
    }

    private val debug = DebugSettings()

    private enum class State {
        INITIALIZING,
        BALL_ATTACHED,
        BALL_MOVING,
        DEAD,
        WON,
    }
    private var state = State.INITIALIZING

    private var livesLost = 0
    private val rectGameArea = Rect()
    private var brickPattern = gameplay.brickPattern.value

    private val paddle = Paddle()
    private val ball = Ball()
    private val bricks = mutableSetOf<Brick>()

    private var shakeAmount = 0f
        set(value) { field = min(value, MAX_SHAKE) }

    // Used to reduce collision calculations
    private data class CollisionArea(val x: Int, val y: Int)
    private class CollisionAreas(val w: Int, val h: Int, private val gameArea: Rect) {
        val collidables = mutableMapOf<CollisionArea, MutableSet<Collidable>>()
        fun addCollidable(collidable: Collidable) {
            for (area in toCollisionAreas(collidable.shape)) {
                collidables.getOrPut(area) { mutableSetOf() }.add(collidable)
            }
        }
        fun removeCollidable(collidable: Collidable) {
            collidables.values.forEach { collidables -> collidables.remove(collidable) }
        }
        fun updateCollidable(collidable: Collidable) {
            removeCollidable(collidable)
            addCollidable(collidable)
        }

        /**
         * Return all [Collidable]s found across all the [CollisionArea]s overlapping a given area.
         */
        fun findNearbyCollidables(rect: Rect): Iterable<Collidable> {
            // The same collidables may exist in multiple areas, so we convert to set to remove
            // duplicates.
            return toCollisionAreas(rect)
                .mapNotNull { area -> collidables[area] }
                .flatten()
                .toSet()
        }

        /**
         * Return all [CollisionArea]s overlapping a given area.
         */
        fun toCollisionAreas(rect: Rect): Iterable<CollisionArea> {
            val topLeftArea = toCollisionArea(rect.x, rect.y)
            val botRightArea = toCollisionArea(rect.right, rect.bottom)

            return Iterable {
                iterator {
                    for (i in topLeftArea.x..botRightArea.x) {
                        for (j in topLeftArea.y..botRightArea.y) {
                            yield(CollisionArea(i, j))
                        }
                    }
                }
            }
        }

        @Suppress("NAME_SHADOWING") // Convert abs pos to local pos
        fun toCollisionArea(x: Float, y: Float): CollisionArea {
            val x = x - gameArea.x
            val y = y - gameArea.y
            val areaW = (gameArea.w) / w
            val areaH = (gameArea.h) / h
            val areaX = (x / areaW).toInt()
            val areaY = (y / areaH).toInt()

            return CollisionArea(areaX, areaY)
        }

        fun toRect(area: CollisionArea): Rect {
            val areaW = (gameArea.w) / w
            val areaH = (gameArea.h) / h

            return Rect(gameArea.x + areaW * area.x, gameArea.y + areaH * area.y, areaW, areaH)
        }
    }
    private lateinit var collisionAreas: CollisionAreas

    fun handlePress(x: Float, y: Float) {
        if (state == State.BALL_ATTACHED || state == State.BALL_MOVING || state == State.DEAD) {
            val prevPaddleX = paddle.shape.centerX
            paddle.shape.centerX = x
            paddle.shape.x = paddle.shape.x.coerceIn(
                rectGameArea.x,
                rectGameArea.right - paddle.shape.w
            )
            collisionAreas.updateCollidable(paddle)

            if (state == State.BALL_ATTACHED) {
                ball.shape.x += paddle.shape.centerX - prevPaddleX
            }
        }
    }

    fun handleRelease() {
        if (state == State.BALL_ATTACHED) {
            // Choose initial angle based on how far left or right you are
            val percent = (paddle.shape.centerX - rectGameArea.x) / rectGameArea.w
            val initialDir = PI.toFloat() + PI.toFloat() * percent
            ball.vel.setByAngle(initialDir, gameplay.ballSpeed.value)
            state = State.BALL_MOVING
        }
    }

    fun update(elapsedSecs: Float) {
        val elapsedSecs = elapsedSecs / debug.slowdownMultiplier.value
        when (state) {
            State.INITIALIZING -> {
                initializeWorld()
                state = State.BALL_ATTACHED
            }
            State.BALL_ATTACHED -> attachBall()
            State.BALL_MOVING -> updateBallMoving(elapsedSecs)
            State.DEAD -> {
                ++livesLost
                state = State.BALL_ATTACHED
            }
            State.WON -> {
                livesLost = 0
                state = State.INITIALIZING
            }
        }

        if (shakeAmount > 0f) {
            shakeAmount -= ui.shakeDampening.value * elapsedSecs
            shakeAmount = max(shakeAmount, 0f)
        }
    }


    private fun updateBrickPattern() {
        brickPattern = gameplay.brickPattern.value
        state = State.INITIALIZING
    }

    private fun updateBall() {
        ball.shape.radius = gameplay.ballRadius.value
        ball.vel.len = gameplay.ballSpeed.value
    }

    private fun updatePaddle() {
        val paddleBottom = screenSize.y - ui.screenMarginTop.value
        paddle.shape.w = gameplay.paddleWidth.value
        paddle.shape.h = gameplay.paddleHeight.value
        paddle.shape.bottom = paddleBottom
        if (paddle.shape.right > rectGameArea.right) {
            paddle.shape.right = rectGameArea.right
        }
        else if (paddle.shape.x < rectGameArea.x) {
            paddle.shape.x = rectGameArea.x
        }

        collisionAreas.updateCollidable(paddle)
    }

    private fun initializeWorld() {
        shakeAmount = 0f

        // Choose "h" so it just goes past the bottom of the screen, enough that the ball won't
        // bounce off of it
        rectGameArea.set(
            ui.screenMarginSides.value,
            ui.screenMarginSides.value,
            screenSize.x - ui.screenMarginTop.value * 2f,
            screenSize.y - ui.screenMarginTop.value + gameplay.ballRadius.value * 2
        )

        collisionAreas = CollisionAreas(3, 8, rectGameArea)
        initializeBricks(rectGameArea, collisionAreas)
        updateBall()
        updatePaddle()

        if (livesLost == 0) {
            // Only initialize paddle X position on the first life; after that, keep where you last
            // were
            paddle.shape.centerX = screenSize.x / 2f
        }

        attachBall()
    }

    private fun initializeBricks(gameArea: Rect, collisionAreas: CollisionAreas) {
        bricks.clear()

        // Gap between each brick as well as between outside bricks and the wall
        val brickGap = 10f
        val numBricksX = 6
        val numBricksY = 15
        val brickW = (gameArea.w - ((numBricksX + 1) * brickGap)) / numBricksX
        val brickH = brickW / 3f
        val topBrickY = gameArea.y + (gameArea.h / 10f)

        val brickTypeProvider: (i: Int, j: Int, w: Int, h: Int) -> Brick.Type? = when (brickPattern) {
            BrickPattern.EASY -> { _, _, _, _ -> Brick.Type.WEAK }
            BrickPattern.GRADIENT -> { _, j, _, h ->
                val bucketSize = h / Brick.Type.values().size
                val ordinalMax = Brick.Type.values().size - 1
                val ordinal = min(j / bucketSize, ordinalMax)
                // Return brick types in reverse, from toughest to weakest on the way down
                Brick.Type.values()[ordinalMax - ordinal]
            }
            BrickPattern.LINES -> { i, _, w, _ -> if ((i % 2) == 0) Brick.Type.WEAK else Brick.Type.STRONG }
            else -> { _, _, _, _ -> Brick.Type.values().random() }
        }

        var brickX = gameArea.x + brickGap
        for (brickI in 0 until numBricksX) {
            var brickY = topBrickY
            for (brickJ in 0 until numBricksY) {
                val brickType = brickTypeProvider(brickI, brickJ, numBricksX, numBricksY) ?: continue
                val brick = Brick(brickType, ui)
                brick.shape.set(brickX, brickY, brickW, brickH)
                bricks.add(brick)
                collisionAreas.addCollidable(brick)

                brickY += brickH + brickGap
            }
            brickX += brickW + brickGap
        }
    }

    private fun attachBall() {
        ball.vel.reset()
        ball.shape.x = paddle.shape.centerX
        ball.shape.bottom = paddle.shape.y
    }

    private fun updateBallMoving(elapsedSecs: Float) {
        val ballBoundsBefore = ball.shape.toBoundingRect()
        ball.shape.x += ball.vel.x * elapsedSecs
        ball.shape.y += ball.vel.y * elapsedSecs

        if ((ball.vel.x < 0 && ball.shape.left < rectGameArea.x)
            || (ball.vel.x > 0 && ball.shape.right > rectGameArea.right))
        {
            ball.vel.x = -ball.vel.x
            shakeAmount += ui.wallShakeMagnitude.value
        }

        if ((ball.vel.y < 0 && ball.shape.top < rectGameArea.y)
            || (ball.vel.y > 0 && ball.shape.bottom > screenSize.y && debug.bounceOffBottom.value)) {
            ball.vel.y = -ball.vel.y
            shakeAmount += ui.wallShakeMagnitude.value
        }

        if (ball.shape.top > screenSize.y) {
            state = State.DEAD
        }

        // Handle collisions...
        val ballBounds = ball.shape.toBoundingRect()
        for (collidable in collisionAreas.findNearbyCollidables(ballBounds)) {
            var deadBrick: Brick? = null
            if (ballBounds.intersectsWith(collidable.shape)) {
                when(collidable) {
                    is Brick -> {
                        if (!debug.juggernautMode.value) {
                            // Note: We might collide with multiple bricks in a single frame, so
                            // we use "abs" instead of toggling the velocity direction each
                            // collision
                            if (ballBoundsBefore.y > collidable.shape.bottom && ballBounds.y <= collidable.shape.bottom) {
                                ball.vel.y = abs(ball.vel.y)
                            } else if (ballBoundsBefore.bottom < collidable.shape.y && ballBounds.bottom >= collidable.shape.y) {
                                ball.vel.y = -abs(ball.vel.y)
                            }

                            if (ballBoundsBefore.right < collidable.shape.x && ballBounds.right >= collidable.shape.x) {
                                ball.vel.x = -abs(ball.vel.x)
                            } else if (ballBoundsBefore.x > collidable.shape.right && ballBounds.x <= collidable.shape.right) {
                                ball.vel.x = abs(ball.vel.x)
                            }
                        }

                        if (collidable.hit() || debug.juggernautMode.value) {
                            deadBrick = collidable
                            shakeAmount += ui.brickBrokenShakeMagnitude.value
                        }
                        else {
                            shakeAmount += ui.brickHitShakeMagnitude.value
                        }
                    }
                    is Paddle -> {
                        if (ball.vel.y > 0) {
                            val percent = ((ball.shape.x - paddle.shape.x) / paddle.shape.w).coerceIn(.1f, .9f)
                            val ricochetDir = PI.toFloat() + PI.toFloat() * percent
                            ball.vel.setByAngle(ricochetDir, gameplay.ballSpeed.value)
                            shakeAmount += ui.paddleShakeMagnitude.value
                        }
                    }
                }
            }

            deadBrick?.let { brick ->
                collisionAreas.removeCollidable(brick)
                bricks.remove(brick)

                if (bricks.isEmpty()) {
                    state = State.WON
                }
            }
        }
    }

    fun render(renderer: Renderer) {
        renderer.clearScreen(ui.backgroundColor.value)

        val shake = if (shakeAmount > 0f) {
            val shakeX = Random.nextFloat() * shakeAmount
            val shakeY = Random.nextFloat() * shakeAmount
            Vec(shakeX, shakeY)
        } else {
            Vec.EMPTY
        }

        renderer.withTranslate(shake) {
            bricks.forEach { brick ->
                renderer.fillRect(brick.shape, brick.toColor())
            }

            renderer.fillRect(paddle.shape, Colors.WHITE)
            renderer.fillCircle(ball.shape, Colors.YELLOW)

            if (debug.showCollisionArea.value) {
                for (area in collisionAreas.toCollisionAreas(ball.shape.toBoundingRect())) {
                    renderer.drawRect(collisionAreas.toRect(area), Colors.GREEN, 2f)
                }
            }
            if (debug.showCollidables.value) {
                for (collidable in collisionAreas.findNearbyCollidables(ball.shape.toBoundingRect())) {
                    renderer.drawRect(collidable.shape, Colors.MAGENTA, 6f)
                }
            }

            renderer.drawRect(rectGameArea, Colors.WHITE)
        }
    }

}

