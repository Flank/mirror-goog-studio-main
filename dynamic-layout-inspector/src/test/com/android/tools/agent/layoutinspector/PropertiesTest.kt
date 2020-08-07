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

import android.content.res.Resources
import android.graphics.Color
import android.view.inspector.WindowInspector
import androidx.ui.core.AndroidComposeView
import androidx.ui.tooling.inspector.InspectorNode
import androidx.ui.tooling.inspector.NodeParameter
import androidx.ui.tooling.inspector.ParameterType
import androidx.ui.tooling.inspector.TREE_ENTRY
import com.android.tools.agent.layoutinspector.testing.CompanionSupplierRule
import com.android.tools.agent.layoutinspector.testing.ResourceEntry
import com.android.tools.agent.layoutinspector.testing.StandardView
import com.android.tools.agent.layoutinspector.testing.StringTable
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.transport.AgentRule
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class PropertiesTest {

    @get:Rule
    val supplier = CompanionSupplierRule()

    @get:Rule
    val agentRule = AgentRule()

    private val fontId = 17

    private val node =
        InspectorNode(
            id = 77,
            name = "Column",
            fileName = "MainActivity.kt",
            packageHash = 1777,
            offset = 400,
            lineNumber = 26,
            left = 55,
            top = 121,
            width = 421,
            height = 269,
            children = emptyList(),
            parameters = listOf(
                NodeParameter("name", ParameterType.String, "Hello"),
                NodeParameter("wrap", ParameterType.Boolean, true),
                NodeParameter("number", ParameterType.Double, 321.5),
                NodeParameter("factor", ParameterType.Float, 1.5f),
                NodeParameter("count", ParameterType.Int32, 17),
                NodeParameter("largeCount", ParameterType.Int64, 17L),
                NodeParameter("color", ParameterType.Color, Color.BLUE),
                NodeParameter("resId", ParameterType.Resource, fontId),
                NodeParameter("elevation", ParameterType.DimensionDp, 2.0f),
                NodeParameter("fontSize", ParameterType.DimensionSp, 12.0f),
                NodeParameter("baseLineOffset", ParameterType.DimensionEm, 0.2f),
                NodeParameter(
                    "rect", ParameterType.String, "Rect", listOf(
                        NodeParameter("x", ParameterType.DimensionDp, 2.0f),
                        NodeParameter("y", ParameterType.DimensionDp, 2.0f)
                    )
                )
            )
        )

    private companion object {
        init {
            System.loadLibrary("jni-test")
        }
    }

    @Before
    fun before() {
        WindowInspector.getGlobalWindowViews().clear()
    }

    @Test
    fun testProtoBuilder() {
        val view = StandardView.createTextView()
        WindowInspector.getGlobalWindowViews().add(view)
        val properties = Properties()
        properties.handleGetProperties(view.uniqueDrawingId, 19)
        val event = agentRule.events.poll(5, TimeUnit.SECONDS)!!
        val propertyEvent = event.layoutInspectorEvent.properties
        var index = 0
        assertThat(propertyEvent.viewId).isEqualTo(view.uniqueDrawingId)
        assertThat(propertyEvent.generation).isEqualTo(19)
        with (PropertyChecker(propertyEvent)) {
            check(index++, "focused", Type.BOOLEAN, 1)
            check(index++, "byte", Type.BYTE, 7)
            check(index++, "char", Type.CHAR, 'g'.toInt())
            check(index++, "double", Type.DOUBLE, 3.75)
            check(index++, "scaleX", Type.FLOAT, 1.75f)
            check(index++, "scrollX", Type.INT32, 10)
            check(index++, "long", Type.INT64, 7000L)
            check(index++, "short", Type.INT16, 70)
            check(index++, "transitionName", Type.STRING, "MyTransitionName")
            check(index++, "backgroundTint", Type.COLOR, Color.BLUE)
            check(index++, "background", Type.COLOR, Color.YELLOW)
            check(index++, "outlineSpotShadowColor", Type.COLOR, Color.RED)
            check(index++, "foregroundGravity", Type.GRAVITY,
                ImmutableSet.of("top", "fill_horizontal"))
            check(index++, "visibility", Type.INT_ENUM, "invisible")
            check(index++, "labelFor", Type.RESOURCE, ResourceEntry("id", "pck", "other"))
            check(index++, "scrollIndicators", Type.INT_FLAG, ImmutableSet.of("left", "bottom"))
            check(index++, "text", Type.STRING, "Hello World!")
            check(index++, "layout_width", Type.INT_ENUM, "match_parent")
            check(index++, "layout_height", Type.INT32, 400)
            check(index++, "layout_marginBottom", Type.INT32, 10)
            check(index++, "layout_gravity", Type.GRAVITY, ImmutableSet.of("end"))
            assertThat(size).isEqualTo(index)
        }
    }

    @Test
    fun testParameterProtoBuilder() {
        val androidComposeView = mock(AndroidComposeView::class.java)
        val resources = mock(Resources::class.java)
        `when`(androidComposeView.getTag(eq(TREE_ENTRY))).thenReturn(listOf(node))
        `when`(androidComposeView.resources).thenReturn(resources)
        `when`(resources.getResourceTypeName(eq(fontId))).thenReturn("font")
        `when`(resources.getResourcePackageName(eq(fontId))).thenReturn("pck")
        `when`(resources.getResourceEntryName(eq(fontId))).thenReturn("garamond")
        val builder = TreeBuilderWrapper(PropertiesTest::class.java.classLoader!!)
        val nodes = builder.convert(androidComposeView)
        WindowInspector.getGlobalWindowViews().add(androidComposeView)

        val properties = Properties()
        properties.setComposeParameters(nodes.associateBy({ it.id }, { it.parameters }))
        properties.handleGetProperties(node.id, 21)
        val event = agentRule.events.poll(5, TimeUnit.SECONDS)!!
        val propertyEvent = event.layoutInspectorEvent.properties
        var index = 0
        assertThat(propertyEvent.viewId).isEqualTo(node.id)
        assertThat(propertyEvent.generation).isEqualTo(21)
        with (PropertyChecker(propertyEvent)) {
            check(index++, "name", Type.STRING, "Hello")
            check(index++, "wrap", Type.BOOLEAN, 1)
            check(index++, "number", Type.DOUBLE, 321.5)
            check(index++, "factor", Type.FLOAT, 1.5f)
            check(index++, "count", Type.INT32, 17)
            check(index++, "largeCount", Type.INT64, 17L)
            check(index++, "color", Type.COLOR, Color.BLUE)
            check(index++, "resId", Type.RESOURCE, ResourceEntry("font", "pck", "garamond"))
            check(index++, "elevation", Type.DIMENSION_DP, 2.0f)
            check(index++, "fontSize", Type.DIMENSION_SP, 12.0f)
            check(index++, "baseLineOffset", Type.DIMENSION_EM, 0.2f)
            check(index, 0, "x", Type.DIMENSION_DP, 2.0f)
            check(index, 1, "y", Type.DIMENSION_DP, 2.0f)
            check(index++, "rect", Type.STRING, "Rect")
            assertThat(size).isEqualTo(index)
        }
    }

    private class PropertyChecker(proto: LayoutInspectorProto.PropertyEvent) {
        private val table = StringTable(proto.stringList)
        private val properties = proto.propertyList

        val size = properties.size

        fun check(index: Int, name: String, type: Type, value: Any?) {
            check(properties[index], name, type, value)
        }

        fun check(index: Int, element: Int, name: String, type: Type, value: Any?) {
            check(properties[index].elementList[element], name, type, value)
        }

        private fun check(property: Property, name: String, type: Type, value: Any?) {
            assertThat(table[property.name]).isEqualTo(name)
            assertThat(property.type).isEqualTo(type)
            @Suppress("UNCHECKED_CAST")
            when (type) {
                Type.BOOLEAN,
                Type.BYTE,
                Type.CHAR,
                Type.COLOR,
                Type.INT16,
                Type.INT32 -> assertThat(property.int32Value).isEqualTo(value)
                Type.INT64 -> assertThat(property.int64Value).isEqualTo(value)
                Type.DOUBLE -> assertThat(property.doubleValue).isEqualTo(value)
                Type.DIMENSION_DP,
                Type.DIMENSION_SP,
                Type.DIMENSION_EM,
                Type.FLOAT -> assertThat(property.floatValue).isEqualTo(value)
                Type.STRING,
                Type.INT_ENUM -> assertThat(table[property.int32Value]).isEqualTo(value)
                Type.GRAVITY,
                Type.INT_FLAG -> assertThat(
                    property.flagValue.flagList.map { id: Int? -> table[id!!] }
                ).containsExactlyElementsIn(value as Set<String>)
                Type.RESOURCE -> assertThat(table[property.resourceValue]).isEqualTo(value)
                else -> Assert.fail("Unmapped name: $name, type: $type")
            }
        }
    }
}
