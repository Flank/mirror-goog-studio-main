package com.example.android.kotlin

import android.app.Activity
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.android.kotlin.databinding.ActivityLayoutBinding

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding : ActivityLayoutBinding = DataBindingUtil.setContentView(this, R.layout.activity_layout)
        binding.model = ViewModel("foo", "bar")
    }
}
