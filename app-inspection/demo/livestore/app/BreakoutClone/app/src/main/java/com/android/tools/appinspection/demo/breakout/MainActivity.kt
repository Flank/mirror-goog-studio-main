package com.android.tools.appinspection.demo.breakout

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.core.view.doOnLayout
import com.android.tools.appinspection.demo.breakout.databinding.ActivityMainBinding
import com.android.tools.appinspection.demo.livestore.breakout.model.game.GameLoop
import com.android.tools.appinspection.demo.livestore.breakout.model.game.GameWorld
import com.android.tools.appinspection.demo.livestore.breakout.model.math.Vec

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val binding = ActivityMainBinding.inflate(layoutInflater)
    binding.gameArea.doOnLayout {
      val gameWorld = GameWorld(Vec(binding.gameArea.width, binding.gameArea.height))
      val gameLoop = object : GameLoop() {
        override fun handleFrame(elapsedSecs: Float) {
          gameWorld.update(elapsedSecs)
        }
      }
      binding.gameArea.init(gameLoop, gameWorld)
    }
    setContentView(binding.root)
  }
}
