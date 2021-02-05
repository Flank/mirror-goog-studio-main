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

package android.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting

// Normally this class is abstract, but for tests, making it concrete allows us to set up
// layout trees more easily.
@Suppress("UNUSED_PARAMETER")
open class View @VisibleForTesting constructor(val context: Context) {

    class AttachInfo {

        @Suppress("unused")
        // Name is important: Accessed via reflection
        private val mHandler = Handler(Looper.getMainLooper())

        // Name is important: Accessed via reflection
        private val mThreadedRenderer = ThreadedRenderer()

        internal fun forcePictureCapture(picture: Picture) {
            mThreadedRenderer.pictureCaptureCallback?.onPictureCaptured(picture)
        }
    }

    companion object {

        const val VISIBLE = 0x0
        const val INVISIBLE = 0x4
        const val GONE = 0x8
    }

    val id = context.generateViewId()
    // uniqueDrawingId is distinct from id in production, but for testing purposes, relating them is
    // fine. That said, we transform the value to make sure we don't allow them to be used
    // interchangeably by accident.
    val uniqueDrawingId = id.toLong() * 2

    val visibility = VISIBLE
    val isAttachedToWindow = true
    val z = 0f
    val drawableState = intArrayOf()

    val resources: Resources = context.resources
    val sourceLayoutResId = 0
    val attributeSourceResourceMap: Map<Int, Int> = mapOf()

    var left: Int = 0
    var top: Int = 0
    var width: Int = 0
    var height: Int = 0
    var scrollX: Int = 0
    var scrollY: Int = 0
    var layoutParams: ViewGroup.LayoutParams = ViewGroup.LayoutParams()

    @VisibleForTesting
    val locationInSurface = Point(0, 0)

    @VisibleForTesting
    val locationOnScreen = Point(0, 0)

    var drawHandler: (Canvas) -> Unit = {}

    // Name is important: Accessed via reflection
    private var mAttachInfo: AttachInfo? = null

    @VisibleForTesting
    fun setAttachInfo(attachInfo: AttachInfo) {
        mAttachInfo = attachInfo
    }

    fun getLocationInSurface(location: IntArray) {
        location[0] = locationInSurface.x
        location[1] = locationInSurface.y
    }

    fun getLocationOnScreen(location: IntArray) {
        location[0] = locationOnScreen.x
        location[1] = locationOnScreen.y
    }

    fun getAttributeResolutionStack(attributeId: Int) = intArrayOf()

    fun invalidate() {}

    fun draw(canvas: Canvas) = drawHandler(canvas)

    // Only works with views that were constructed with an AttachInfo
    @VisibleForTesting // Normally, the rendering system triggers this
    fun forcePictureCapture(picture: Picture) {
        mAttachInfo!!.forcePictureCapture(picture)
    }
}
