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

package com.android.tools.agent.appinspection.proto

import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Point
import android.os.Build
import android.util.AndroidRuntimeException
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import com.android.tools.agent.appinspection.framework.getChildren
import com.android.tools.agent.appinspection.framework.getTextValue
import com.android.tools.agent.appinspection.proto.property.PropertyCache
import com.android.tools.agent.appinspection.proto.property.SimplePropertyReader
import com.android.tools.agent.appinspection.proto.resource.convert
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.AppContext
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Bounds
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertyGroup
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Quad
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Rect
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ViewNode
import kotlin.math.roundToInt

/**
 * Convert the target [View] into a proto [ViewNode].
 *
 * This method must be called on the main thread to avoid race conditions when querying the tree.
 */
fun View.toNode(stringTable: StringTable): ViewNode {
    ThreadUtils.assertOnMainThread()

    // Screen location is (0, 0) for main window but useful inside floating dialogs
    val screenLocation = IntArray(2)
    getLocationOnScreen(screenLocation)

    val absPos = Point(screenLocation[0], screenLocation[1])
    return toNodeImpl(stringTable, absPos).build()
}

/**
 * Directly convert a view to a node, recursively adding children.
 */
private fun View.toNodeImpl(
    stringTable: StringTable,
    absOffset: Point
): ViewNode.Builder {
    val view = this
    val viewClass = view::class.java
    val absPos = Point(absOffset.x + view.left, absOffset.y + view.top)

    return ViewNode.newBuilder().apply {
        id = uniqueDrawingId

        createResource(stringTable, view.id)?.let { resource = it }
        className = stringTable.put(viewClass.simpleName)
        viewClass.`package`?.name?.let { packageName = stringTable.put(it) }

        bounds = Bounds.newBuilder().apply {
            layout = Rect.newBuilder().apply {
                x = absPos.x
                y = absPos.y
                w = view.width
                h = view.height
            }.build()

            val transform = Matrix()
            view.transformMatrixToGlobal(transform)
            if (!transform.isIdentity) {
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                val corners = floatArrayOf(
                    0f, 0f,
                    w, 0f,
                    w, h,
                    0f, h,
                )
                transform.mapPoints(corners)
                render = Quad.newBuilder().apply {
                    x0 = corners[0].roundToInt()
                    y0 = corners[1].roundToInt()
                    x1 = corners[2].roundToInt()
                    y1 = corners[3].roundToInt()
                    x2 = corners[4].roundToInt()
                    y2 = corners[5].roundToInt()
                    x3 = corners[6].roundToInt()
                    y3 = corners[7].roundToInt()
                }.build()
            }
        }.build()

        createResource(stringTable, view.sourceLayoutResId)?.let { layoutResource = it }
        (view.layoutParams as? WindowManager.LayoutParams)?.let { params ->
            layoutFlags = params.flags
        }

        view.getTextValue()?.let { text ->
            textValue = stringTable.put(text)
        }
        if (view is ViewGroup) {
            view.getChildren().forEach { child ->
                addChildren(child.toNodeImpl(stringTable, Point(absPos.x - scrollX, absPos.y - scrollY)))
            }
        }
    }
}

/**
 * Search this view for a resource with matching [resourceId] and, if found, return its
 * proto representation.
 */
fun View.createResource(stringTable: StringTable, resourceId: Int): Resource? {
    if (resourceId <= 0) return null

    return try {
        return Resource.newBuilder().apply {
            type = stringTable.put(resources.getResourceTypeName(resourceId))
            namespace = stringTable.put(resources.getResourcePackageName(resourceId))
            name = stringTable.put(resources.getResourceEntryName(resourceId))
        }.build()
    } catch (ex: Resources.NotFoundException) {
        null
    }
}

fun View.getNamespace(attributeId: Int): String =
    if (attributeId != 0) resources.getResourcePackageName(attributeId) else ""

fun View.createAppContext(stringTable: StringTable): AppContext {
    val size = windowSize
    return AppContext.newBuilder().apply {
        createResource(stringTable, context.themeResId)?.let { themeResource ->
            theme = themeResource
        }
        screenWidth = size.x
        screenHeight = size.y
    }.build()
}

private val View.windowSize: Point
  get() {
      val windowManager = context.getSystemService(WindowManager::class.java)
      return if (Build.VERSION.SDK_INT >= 30) {
          val bounds = windowManager.currentWindowMetrics.bounds
          Point(bounds.width(), bounds.height())
      } else {
          val display = windowManager.defaultDisplay
          val size = Point()
          display.getRealSize(size)
          size
      }
  }

fun View.createConfiguration(stringTable: StringTable) =
    context.resources.configuration.convert(stringTable)

fun View.createGetPropertiesResponse(): GetPropertiesResponse {
    val stringTable = StringTable()
    val view = this

    return GetPropertiesResponse.newBuilder().apply {
        propertyGroup = view.createPropertyGroup(stringTable)
        addAllStrings(stringTable.toStringEntries())
    }.build()
}

fun View.createPropertyGroup(stringTable: StringTable): PropertyGroup {
    // In general, run off the main thread so we don't block the app doing expensive work.
    ThreadUtils.assertOffMainThread()
    return if (this !is WebView) {
        try {
            createPropertyGroupImpl(stringTable)
        }
        catch (ex: AndroidRuntimeException) {
            // Some properties require work performed on the UIThread before it is accessible.
            // Example: View.resolvePadding() is called from View.getPaddingLeft()
            ThreadUtils.runOnMainThread { createPropertyGroupImpl(stringTable) }.get()
        }
    }
    else {
        // WebView uniquely throws exceptions if you try to read its properties off the main thread,
        // so we have no choice in this case.
        ThreadUtils.runOnMainThread { createPropertyGroupImpl(stringTable) }.get()
    }
}

private fun View.createPropertyGroupImpl(stringTable: StringTable): PropertyGroup {
    val viewCacheMap = PropertyCache.createViewCache()
    val layoutCacheMap = PropertyCache.createLayoutParamsCache()

    val viewCache = viewCacheMap.typeOf(this)
    val layoutCache = layoutCacheMap.typeOf(layoutParams)

    val viewProperties = viewCache.properties
    val layoutProperties = layoutCache.properties

    val viewReader =
        SimplePropertyReader(
            stringTable,
            this,
            viewProperties,
            SimplePropertyReader.PropertyCategory.VIEW
        )
    viewCache.readProperties(this, viewReader)
    val layoutReader =
        SimplePropertyReader(
            stringTable,
            this,
            layoutProperties,
            SimplePropertyReader.PropertyCategory.LAYOUT_PARAMS
        )
    layoutCache.readProperties(layoutParams, layoutReader)

    val view = this
    return PropertyGroup.newBuilder().apply {
        this.viewId = view.uniqueDrawingId

        view.createResource(stringTable, sourceLayoutResId)?.let { layoutResource ->
            layout = layoutResource
        }

        (viewProperties + layoutProperties)
            .mapNotNull { property -> property.build(stringTable, view) }
            .forEach { property -> this.addProperty(property) }
    }.build()
}
