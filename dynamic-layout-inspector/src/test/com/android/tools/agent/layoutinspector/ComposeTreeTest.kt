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

import android.util.Log
import android.widget.RootLinearLayout
import androidx.ui.core.AndroidComposeView
import com.android.tools.agent.layoutinspector.testing.ComposeViewResult
import com.android.tools.agent.layoutinspector.testing.ResultViewReader
import com.android.tools.agent.layoutinspector.testing.SlotTableReader
import com.android.tools.agent.layoutinspector.testing.StandardView
import com.android.tools.agent.layoutinspector.testing.StringTable
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComposeTreeTest {

    @Test
    fun testSimpleComposeExample() {
        val slots = SlotTableReader()
        slots.read("simple-slot-table.csv")
        val expected = ResultViewReader()
        expected.viewLeft = 55
        expected.viewTop = 121
        expected.read("simple-result.csv")

        System.loadLibrary("jni-test")
        val event = ComponentTreeTest.allocateEvent()

        val linearLayout = StandardView.createLinearLayoutWithComposeView()
        val treeBuilder = ComponentTree(true)
        treeBuilder.writeTree(event, linearLayout)

        val proto = ComponentTreeEvent.parseFrom(ComponentTreeTest.toByteArray(event))
        val table = StringTable(proto.stringList)
        val layout = proto.root
        assertThat(table[layout.className]).isEqualTo(RootLinearLayout::class.java.simpleName)
        assertThat(layout.subViewCount).isEqualTo(1)
        val view = layout.subViewList.first()
        assertThat(table[view.className]).isEqualTo(AndroidComposeView::class.java.simpleName)
        assertThat(view.subViewCount).isEqualTo(1)
        checkComposeView(table, view.subViewList.first(), expected.roots.single())
    }

    @Test
    fun testDebugFlagsAreOffForCheckIn() {
        assertThat(ComposeTree.DEBUG_COMPOSE).isFalse()
        assertThat(Log.DEBUG_LOG_IN_TESTS).isFalse()
    }

    private fun checkComposeView(
        table: StringTable,
        view: LayoutInspectorProto.View,
        expected: ComposeViewResult
    ) {
        val name = "Mismatch in line ${expected.csvLineNumber}"
        assertThat(table[view.className]).named(name).isEqualTo(expected.className)
        val actualInvocation = "${table[view.composePackage]}.${table[view.composeInvocation]}"
        assertThat(actualInvocation).named(name).isEqualTo(expected.invocation)
        assertThat(table[view.composeFilename]).named(name).isEqualTo(expected.fileName)
        assertThat(view.composeLineNumber).named(name).isEqualTo(expected.lineNumber)
        assertThat(view.x).named(name).isEqualTo(expected.left)
        assertThat(view.y).named(name).isEqualTo(expected.top)
        assertThat(view.x + view.width).named(name).isEqualTo(expected.right)
        assertThat(view.y + view.height).named(name).isEqualTo(expected.bottom)
        assertThat(view.subViewCount).named(name).isEqualTo(expected.children.size)
        for (index in view.subViewList.indices) {
            checkComposeView(table, view.getSubView(index), expected.children[index])
        }
    }
}
