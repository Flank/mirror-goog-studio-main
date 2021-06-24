/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_SRC_COMPAT
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.TAG_ANIMATED_VECTOR
import com.android.SdkConstants.TAG_VECTOR
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import org.w3c.dom.Attr
import java.io.File

/**
 * Finds all the vector drawables and checks references to them in
 * layouts.
 *
 * This detector looks for common mistakes related to AppCompat support
 * for vector drawables, that is:
 * * Using app:srcCompat without useSupportLibrary in build.gradle
 * * Using android:src with useSupportLibrary in build.gradle
 */
class VectorDrawableCompatDetector : ResourceXmlDetector() {
    companion object {
        /** The main issue discovered by this detector */
        @JvmField
        val ISSUE = create(
            id = "VectorDrawableCompat",
            briefDescription = "Using VectorDrawableCompat",
            explanation = """
                To use VectorDrawableCompat, you need to make two modifications to your project. \
                First, set `android.defaultConfig.vectorDrawables.useSupportLibrary = true` in your \
                `build.gradle` file, and second, use `app:srcCompat` instead of `android:src` to \
                refer to vector drawables.""",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                VectorDrawableCompatDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
            .addMoreInfo("https://developer.android.com/guide/topics/graphics/vector-drawable-resources")
            .addMoreInfo("https://medium.com/androiddevelopers/using-vector-assets-in-android-apps-4318fd662eb9")
    }

    /** Whether to skip the checks altogether. */
    private var skipChecks = false

    /** Whether the project uses AppCompat for vectors. */
    private var useSupportLibrary = false

    override fun beforeCheckRootProject(context: Context) {
        val variant = context.project.buildVariant
        if (variant == null) {
            skipChecks = true
            return
        }
        if (context.project.minSdk >= 21) {
            skipChecks = true
            return
        }
        val version = context.project.gradleModelVersion
        if (version == null || version.major < 2) {
            skipChecks = true
            return
        }
        useSupportLibrary = variant.useSupportLibraryVectorDrawables
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return if (skipChecks)
            false
        else
            folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return if (skipChecks) null else listOf(ATTR_SRC, ATTR_SRC_COMPAT)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (skipChecks) {
            return
        }
        val client = context.client
        val full = context.isGlobalAnalysis()
        val project = if (full) context.mainProject else context.project
        val resources = client.getResources(project, ResourceRepositoryScope.LOCAL_DEPENDENCIES)
        fun isVector(name: String) = checkResourceRepository(resources, name)
        val name = attribute.localName
        val namespace = attribute.namespaceURI
        if (ATTR_SRC == name && ANDROID_URI != namespace || ATTR_SRC_COMPAT == name && AUTO_URI != namespace) {
            // Not the attribute we are looking for.
            return
        }
        val resourceUrl = ResourceUrl.parse(attribute.value) ?: return
        if (useSupportLibrary && ATTR_SRC == name && isVector(resourceUrl.name)) {
            val location = context.getNameLocation(attribute)
            val message = "When using VectorDrawableCompat, you need to use `app:srcCompat`"
            context.report(ISSUE, attribute, location, message)
        } else if (!useSupportLibrary && ATTR_SRC_COMPAT == name && isVector(resourceUrl.name)) {
            val location = context.getNameLocation(attribute)
            var path = FN_BUILD_GRADLE
            val model = context.project.buildModule
            if (model != null) {
                path = model.modulePath + File.separator + path
            }
            val message = "To use VectorDrawableCompat, you need to set " +
                    "`android.defaultConfig.vectorDrawables.useSupportLibrary = true` in `$path`"
            context.report(ISSUE, attribute, location, message)
        }
    }

    private fun checkResourceRepository(resources: ResourceRepository, name: String): Boolean {
        val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.DRAWABLE, name)

        // Check if at least one drawable with this name is a vector.
        for (item in items) {
            val source = item.source ?: return false
            val file = source.toFile() ?: return false
            if (!source.fileName.endsWith(DOT_XML)) {
                continue
            }
            val rootTagName = XmlUtils.getRootTagName(file)
            return TAG_VECTOR == rootTagName || TAG_ANIMATED_VECTOR == rootTagName
        }
        return false
    }
}
