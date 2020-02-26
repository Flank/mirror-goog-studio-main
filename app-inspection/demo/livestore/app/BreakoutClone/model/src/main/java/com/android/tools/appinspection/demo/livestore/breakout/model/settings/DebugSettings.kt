package com.android.tools.appinspection.demo.livestore.breakout.model.settings

import com.android.tools.appinspection.livestore.LiveStore

class DebugSettings() {
    private val store = LiveStore("Debug")

    val slowdownMultiplier = store.addFloat("Slowdown multiplier", 1f, 1f..10f)
    val bounceOffBottom = store.addBool("No death mode", false)
    val juggernautMode = store.addBool("Unstoppable ball", false)
    val showCollidables = store.addBool("Show active collidables", false)
    val showCollisionArea = store.addBool("Show active collision areas", false)
}