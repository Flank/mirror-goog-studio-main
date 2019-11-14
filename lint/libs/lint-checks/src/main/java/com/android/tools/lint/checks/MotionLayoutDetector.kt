/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.MOTION_LAYOUT
import com.android.SdkConstants.MotionSceneTags.MOTION_SCENE
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import java.io.File

/**
 * Various checks for Motion Layout files.
 */
class MotionLayoutDetector : ResourceXmlDetector() {

    private var referencesRecorded = false
    private val resourceModel: ResourceUsageModel by lazy(LazyThreadSafetyMode.NONE) { ResourceUsageModel() }
    @Suppress("RemoveExplicitTypeArguments") // Compiler was unable to infer the type of the expr
    private val references: MutableMap<Resource, Location>
            by lazy<MutableMap<Resource, Location>>(LazyThreadSafetyMode.NONE) { mutableMapOf() }

    override fun appliesTo(folderType: ResourceFolderType) =
        folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML

    override fun getApplicableElements() =
        listOf(MOTION_LAYOUT.oldName(), MOTION_LAYOUT.newName(), MOTION_SCENE)

    override fun afterCheckRootProject(context: Context) {
        if (!referencesRecorded) {
            return
        }
        references.forEach { (reference, location) ->
            val resource = resourceModel.getResource(reference.type, reference.name)
            if (resource == null || !resource.isDeclared) {
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    location,
                    "The motion scene file: ${reference.url} doesn't exist",
                    LintFix.create().name("Create ${reference.url}").data(reference.url))
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            MOTION_SCENE -> visitMotionScene(context, element)
            else -> visitMotionLayout(context, element)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun visitMotionScene(context: XmlContext, element: Element) {
        val resourceFolderType = context.resourceFolderType ?: return
        val resourceType = ResourceType.fromFolderName(resourceFolderType.getName()) ?: return
        val name = context.file.nameWithoutExtension
        resourceModel.addDeclaredResource(resourceType, name, null, true)
    }

    private fun visitMotionLayout(context: XmlContext, element: Element) {
        val description = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (description == null) {
            val sceneUrl = motionSceneUrlFromMotionLayoutFileName(context)
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "The attribute: `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is missing",
                LintFix.create().name("Create $sceneUrl and set attribute").data(sceneUrl))
        }
        else {
            val resource = resourceModel.getResourceFromUrl(description.value)
            if (resource != null && resource.type == ResourceType.XML) {
                references[resource] = context.getValueLocation(description)
                referencesRecorded = true
                if (isIncrementalMode(context)) {
                    processMotionSceneFile(context, resource)
                }
            }
            else {
                val sceneUrl = motionSceneUrlFromMotionLayoutFileName(context)
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    context.getValueLocation(description),
                    "`${description.value}` is an invalid value for $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION",
                    LintFix.create().name("Create $sceneUrl and set attribute").data(sceneUrl))
            }
        }
    }

    private fun isIncrementalMode(context: XmlContext): Boolean =
        !context.scope.contains(Scope.ALL_RESOURCE_FILES)

    private fun motionSceneUrlFromMotionLayoutFileName(context: XmlContext): String =
        "@xml/${context.file.nameWithoutExtension}_scene"

    private fun processMotionSceneFile(context: XmlContext, resource: Resource) {
        val motionFolder = File(context.file.parentFile.parentFile, "xml")
        val motionFile = File(motionFolder, "${resource.name}$DOT_XML")
        val contents = context.client.readFile(motionFile)
        if (contents.isEmpty()) {
            return
        }
        val xml = contents.toString()
        val document = context.parser.parseXml(xml, motionFile) ?: return
        val name = motionFile.nameWithoutExtension
        val root = document.documentElement ?: return
        if (root.tagName == MOTION_SCENE) {
            resourceModel.addDeclaredResource(ResourceType.XML, name, null, true)
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a MotionLayout.
                The $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION is required to specify a valid motion \
                scene file.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}
