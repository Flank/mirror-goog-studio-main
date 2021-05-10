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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import com.android.tools.agent.appinspection.framework.getChildren
import com.android.tools.agent.appinspection.framework.getTextValue
import com.android.tools.agent.appinspection.framework.isSystemView
import com.android.tools.agent.appinspection.proto.property.PropertyCache
import com.android.tools.agent.appinspection.proto.property.SimplePropertyReader
import com.android.tools.agent.appinspection.proto.resource.convert
import com.android.tools.agent.appinspection.util.ThreadUtils
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.AppContext
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Bounds
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertyGroup
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Quad
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Rect
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ViewNode
import kotlin.math.roundToInt

/**
 * Convert the target [View] into a proto [ViewNode].
 *
 * This method must be called on the main thread to avoid race conditions when querying the tree.
 */
fun View.toNode(stringTable: StringTable, skipSystemViews: Boolean): ViewNode {
    ThreadUtils.assertOnMainThread()

    // Screen location is (0, 0) for main window but useful inside floating dialogs
    val screenLocation = IntArray(2)
    getLocationOnScreen(screenLocation)

    val absPos = Point(screenLocation[0], screenLocation[1])
    val rootNode = this.toNodeImpl(stringTable, absPos)
    populateNodesRecursively(stringTable, skipSystemViews, rootNode, absPos)

    return rootNode.build()
}

/**
 * Run through all views and, if not filtered out, created nodes for them.
 *
 * It is because of the fact we can filter out some intermediate nodes that we need to have a
 * separate method for populating nodes and creating them (see: [toNodeImpl]
 *
 * Any valid children of intermediate views that got filtered out will be added to their first
 * valid ancestor.
 */
private fun View.populateNodesRecursively(
    stringTable: StringTable,
    skipSystemViews: Boolean,
    lastParent: ViewNode.Builder,
    parentPos: Point,
) {
    if (this is ViewGroup) {
        for (child in getChildren()) {
            val childPos =
                Point(parentPos.x + child.left - scrollX, parentPos.y + child.top - scrollY)
            val childNode =
                if (!(skipSystemViews && child.isSystemView())) {
                    child.toNodeImpl(stringTable, childPos)
                } else null

            // Filling out protos requires populating children before parents, since "addChildren"
            // takes a snapshot of the current node when you call it.
            child.populateNodesRecursively(
                stringTable,
                skipSystemViews,
                childNode ?: lastParent,
                childPos
            )
            if (childNode != null) {
                lastParent.addChildren(childNode)
            }
        }
    }
}

/**
 * Directly convert a view to a node, without adding children.
 *
 * Adding children will be handled by [populateNodesRecursively], which may skip over intermediate
 * nodes.
 */
private fun View.toNodeImpl(
    stringTable: StringTable,
    absPos: Point
): ViewNode.Builder {
    val view = this
    val viewClass = view::class.java

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

fun View.createAppContext(stringTable: StringTable): AppContext {
    return AppContext.newBuilder().apply {
        createResource(stringTable, context.themeResId)?.let { themeResource ->
            theme = themeResource
        }
    }.build()
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
        createPropertyGroupImpl(stringTable)
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
            .mapNotNull { property -> property.build(stringTable) }
            .forEach { property -> this.addProperty(property) }
    }.build()
}
