/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.layoutinspector.model

class DisplayInfo internal constructor(node: ViewNode) {
    val willNotDraw: Boolean
    val isVisible: Boolean

    val left: Int
    val top: Int
    val width: Int
    val height: Int
    val scrollX: Int
    val scrollY: Int

    val clipChildren: Boolean
    val translateX: Float
    val translateY: Float
    val scaleX: Float
    val scaleY: Float
    val contentDesc: String?

    init {
        left = getInt(node.getProperty("mLeft", "layout:mLeft"), 0)
        top = getInt(node.getProperty("mTop", "layout:mTop"), 0)
        width = getInt(node.getProperty("getWidth()", "layout:getWidth()"), 10)
        height = getInt(node.getProperty("getHeight()", "layout:getHeight()"), 10)
        scrollX = getInt(node.getProperty("mScrollX", "scrolling:mScrollX"), 0)
        scrollY = getInt(node.getProperty("mScrollY", "scrolling:mScrollY"), 0)

        willNotDraw = getBoolean(node.getProperty("willNotDraw()", "drawing:willNotDraw()"), false)
        clipChildren = getBoolean(
                node.getProperty("getClipChildren()", "drawing:getClipChildren()"), true
        )

        translateX = getFloat(node.getProperty("getTranslationX", "drawing:getTranslationX()"), 0f)
        translateY = getFloat(node.getProperty("getTranslationY", "drawing:getTranslationY()"), 0f)
        scaleX = getFloat(node.getProperty("getScaleX()", "drawing:getScaleX()"), 1f)
        scaleY = getFloat(node.getProperty("getScaleY()", "drawing:getScaleY()"), 1f)

        var descProp = node.getProperty("accessibility:getContentDescription()")
        var contentDescription: String? = if (descProp != null
                && descProp.value != null
                && descProp.value != "null")
            descProp.value
        else
            null
        if (contentDescription == null) {
            descProp = node.getProperty("text:mText")
            contentDescription = if (descProp != null
                    && descProp.value != null
                    && descProp.value != "null")
                descProp.value
            else
                null
        }
        this.contentDesc = contentDescription

        val visibility = node.getProperty("getVisibility()", "misc:getVisibility()")
        isVisible = (visibility == null
                || "0" == visibility.value
                || "VISIBLE" == visibility.value)
    }

    private fun getBoolean(p: ViewProperty?, defaultValue: Boolean): Boolean {
        if (p != null) {
            return try {
                java.lang.Boolean.parseBoolean(p.value)
            } catch (e: NumberFormatException) {
                defaultValue
            }

        }
        return defaultValue
    }

    private fun getInt(p: ViewProperty?, defaultValue: Int): Int {
        if (p != null) {
            return try {
                Integer.parseInt(p.value)
            } catch (e: NumberFormatException) {
                defaultValue
            }

        }
        return defaultValue
    }

    private fun getFloat(p: ViewProperty?, defaultValue: Float): Float {
        if (p != null) {
            return try {
                java.lang.Float.parseFloat(p.value)
            } catch (e: NumberFormatException) {
                defaultValue
            }

        }
        return defaultValue
    }
}
