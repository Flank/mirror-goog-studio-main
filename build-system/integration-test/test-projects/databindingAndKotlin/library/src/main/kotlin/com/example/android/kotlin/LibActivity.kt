package com.example.android.kotlin

import android.app.Activity
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.android.kotlin.lib.R
import com.example.android.kotlin.lib.databinding.LibActivityLayoutBinding

class LibActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding : LibActivityLayoutBinding = DataBindingUtil.setContentView(this, R.layout.lib_activity_layout)
        binding.model = ViewModel("foo", "bar")
    }
}
