/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.agent.appinspection.framework

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.tools.agent.appinspection.util.ThreadUtils
import java.util.Stack

fun ViewGroup.getChildren(): List<View> {
    ThreadUtils.assertOnMainThread()
    return (0 until childCount).map { i -> getChildAt(i) }
}

/**
 * Return this node's text value, if it is a kind of node that has one.
 */
fun View.getTextValue(): String? {
    if (this !is TextView) return null
    return text.toString()
}

/**
 * Return a list of this view and all its children in depth-first order
 */
fun View.flatten(): Sequence<View> {
    ThreadUtils.assertOnMainThread()

    return sequence {
        val toProcess = Stack<View>()
        toProcess.push(this@flatten)

        while (toProcess.isNotEmpty()) {
            val curr = toProcess.pop()
            yield(curr)
            if (curr is ViewGroup) {
                toProcess.addAll(curr.getChildren())
            }
        }
    }
}
