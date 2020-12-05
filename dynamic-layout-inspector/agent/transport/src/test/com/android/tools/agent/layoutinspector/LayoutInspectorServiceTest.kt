/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.agent.layoutinspector

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.Picture
import android.os.Handler
import android.os.Looper
import android.view.AttachInfo
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import com.android.tools.agent.layoutinspector.testing.ResourceEntry
import com.android.tools.agent.layoutinspector.testing.StandardView
import com.android.tools.agent.layoutinspector.testing.StringTable
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.android.tools.transport.AgentRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

class LayoutInspectorServiceTest {
    @get:Rule
    var agentRule = AgentRule()

    private companion object {
        init {
            System.loadLibrary("jni-test")
        }
    }

    @Test
    fun testError() {
        LayoutInspectorService.sendErrorMessage("foo")
        val event = agentRule.events.poll(5, TimeUnit.SECONDS)
        val expected = Common.Event.newBuilder()
            // TODO: use ProcessHandle.current().pid() below once we've switched to jdk11
            .setPid(ManagementFactory.getRuntimeMXBean().name.substringBefore('@').toInt())
            .setGroupId(Common.Event.EventGroupIds.LAYOUT_INSPECTOR_ERROR.number.toLong())
            .setKind(Common.Event.Kind.LAYOUT_INSPECTOR)
            .setLayoutInspectorEvent(LayoutInspectorProto.LayoutInspectorEvent.newBuilder().setErrorMessage("foo"))
            .build()
        assertThat(event).isEqualTo(expected)
    }

    @Test
    fun testComponentTreeEvent() {
        val picture = Picture()
        val pictureBytes = byteArrayOf(1, 2, 3, 4, 5)
        picture.setImage(pictureBytes)

        val (_, callback) = setUpInspectorService()

        val event = onPictureCaptured(callback, picture)
        val expectedPid = ManagementFactory.getRuntimeMXBean().name.substringBefore('@').toInt()
        assertThat(event.pid).isEqualTo(expectedPid)
        assertThat(event.groupId).isEqualTo(1101)
        assertThat(event.kind).isEqualTo(Common.Event.Kind.LAYOUT_INSPECTOR)
        val tree = event.layoutInspectorEvent.tree
        assertThat(tree.payloadType).isEqualTo(LayoutInspectorProto.ComponentTreeEvent.PayloadType.SKP)
        val table = StringTable(tree.stringList)
        val layout = tree.root
        assertThat(layout.drawId).isEqualTo(10)
        assertThat(table[layout.viewId]).isEqualTo(ResourceEntry("id", "pck", "linearLayout1"))
        assertThat(table[layout.layout]).isEqualTo(ResourceEntry("layout", "pck", "main_activity"))
        assertThat(layout.x).isEqualTo(10)
        assertThat(layout.y).isEqualTo(50)
        assertThat(layout.width).isEqualTo(980)
        assertThat(layout.height).isEqualTo(2000)
        assertThat(table[layout.className]).isEqualTo("RootLinearLayout")
        assertThat(table[layout.packageName]).isEqualTo("android.widget")
        assertThat(table[layout.textValue]).isEqualTo("")
        assertThat(layout.layoutFlags).isEqualTo(WindowManager.LayoutParams.FLAG_FULLSCREEN
                or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertThat(layout.subViewCount).isEqualTo(1)

        val textView = layout.getSubView(0)
        assertThat(textView.drawId).isEqualTo(11)
        assertThat(table[textView.viewId]).isEqualTo(ResourceEntry("id", "pck", "textView1"))
        assertThat(table[textView.layout]).isEqualTo(ResourceEntry("layout", "pck", "main_activity"))
        assertThat(textView.x).isEqualTo(100)
        assertThat(textView.y).isEqualTo(200)
        assertThat(textView.width).isEqualTo(400)
        assertThat(textView.height).isEqualTo(30)
        assertThat(table[textView.className]).isEqualTo("TextView")
        assertThat(table[textView.packageName]).isEqualTo("android.widget")
        assertThat(table[textView.textValue]).isEqualTo("Hello World!")
        assertThat(textView.layoutFlags).isEqualTo(0)
        assertThat(textView.subViewCount).isEqualTo(0)

        assertThat(table[tree.resources.appPackageName]).isEqualTo("pck")
        val config = tree.resources.configuration
        assertThat(config.fontScale).isEqualTo(1.5f)
        assertThat(config.countryCode).isEqualTo(310)
        assertThat(config.networkCode).isEqualTo(4)
        assertThat(config.screenLayout).isEqualTo(Configuration.SCREENLAYOUT_SIZE_LARGE or
                Configuration.SCREENLAYOUT_LONG_NO or
                Configuration.SCREENLAYOUT_LAYOUTDIR_RTL)
        assertThat(config.colorMode).isEqualTo(Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_NO or
                Configuration.COLOR_MODE_HDR_YES)
        assertThat(config.touchScreen).isEqualTo(Configuration.TOUCHSCREEN_FINGER)
        assertThat(config.keyboard).isEqualTo(Configuration.KEYBOARD_QWERTY)
        assertThat(config.keyboardHidden).isEqualTo(Configuration.KEYBOARDHIDDEN_YES)
        assertThat(config.hardKeyboardHidden).isEqualTo(Configuration.HARDKEYBOARDHIDDEN_NO)
        assertThat(config.navigation).isEqualTo(Configuration.NAVIGATION_WHEEL)
        assertThat(config.navigationHidden).isEqualTo(Configuration.NAVIGATIONHIDDEN_YES)
        assertThat(config.uiMode).isEqualTo(Configuration.UI_MODE_TYPE_NORMAL or
                Configuration.UI_MODE_NIGHT_YES)
        assertThat(config.density).isEqualTo(367)
        assertThat(config.orientation).isEqualTo(Configuration.ORIENTATION_PORTRAIT)
        assertThat(config.screenWidth).isEqualTo(1080)
        assertThat(config.screenHeight).isEqualTo(2280)

        assertThat(agentRule.payloads[event.layoutInspectorEvent.tree.payloadId])
            .isEqualTo(pictureBytes)
    }

    @Test
    fun testUseScreenshotMode() {
        val bitmap = mock(Bitmap::class.java)
        Bitmap.INSTANCE = bitmap
        val bitmapBytes = (1 .. 1_000_000).map { (it % 256).toByte() }.toByteArray()
        `when`(bitmap.byteCount).thenReturn(1_000_000)
        `when`(bitmap.copyPixelsToBuffer(any()))
            .then { invocation ->
                invocation.getArgument<ByteBuffer>(0).put(bitmapBytes)
                true
            }
        val (service, callback) = setUpInspectorService()

        service.onUseScreenshotModeCommand(true)

        val event = onPictureCaptured(callback, Picture())
        assertThat(event.groupId).isEqualTo(1101)
        assertThat(event.kind).isEqualTo(Common.Event.Kind.LAYOUT_INSPECTOR)
        val tree = event.layoutInspectorEvent.tree
        assertThat(tree.payloadType)
            .isEqualTo(LayoutInspectorProto.ComponentTreeEvent.PayloadType.BITMAP_AS_REQUESTED)
        val payload = agentRule.payloads[event.layoutInspectorEvent.tree.payloadId]
        val inf = Inflater().also { it.setInput(payload) }
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (!inf.finished()) {
            val count = inf.inflate(buffer)
            if (count <= 0) {
                break
            }
            baos.write(buffer, 0, count)
            total += count
        }

        assertThat(total).isEqualTo(1_000_000)
        assertThat(baos.toByteArray()).isEqualTo(bitmapBytes)
    }

    private fun setUpInspectorService()
            : Pair<LayoutInspectorService, HardwareRenderer.PictureCapturedCallback> {
        val handler = mock(Handler::class.java)
        `when`(handler.looper).thenReturn(Looper.myLooper())
        val renderer = HardwareRenderer()
        val info = AttachInfo(handler, renderer)
        val view: View = StandardView.createLinearLayoutWithTextView()
        setField(view, "mAttachInfo", info)
        WindowInspector.setGlobalWindowViews(listOf(view))
        val service = LayoutInspectorService.instance()
        service.startLayoutInspector(view)
        val callback = renderer.pictureCaptureCallback!!
        return Pair(service, callback)
    }

    private fun onPictureCaptured(
        callback: HardwareRenderer.PictureCapturedCallback,
        picture: Picture
    ): Common.Event {
        callback.onPictureCaptured(picture)
        val event = agentRule.events.poll(5, TimeUnit.SECONDS)!!
        return event
    }

    private fun setField(instance: Any, fieldName: String, value: Any) {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field[instance] = value
    }
}
