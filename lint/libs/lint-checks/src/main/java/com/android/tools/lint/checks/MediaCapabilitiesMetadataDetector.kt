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

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getSubTagsByName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UReferenceExpression
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.EnumSet

/**
 * Check which makes sure that an application that uses MediaStore.Video anywhere in code
 * defines its media capabilities in the Manifest to enable transcoding on Android 12+
 */
class MediaCapabilitiesMetadataDetector : Detector(), SourceCodeScanner, ClassScanner, XmlScanner {

    private var foundVideoUsage = false

    override fun getApplicableAsmNodeTypes(): IntArray {
        return intArrayOf(AbstractInsnNode.METHOD_INSN, AbstractInsnNode.FIELD_INSN)
    }

    override fun checkInstruction(
        context: ClassContext,
        classNode: ClassNode,
        method: MethodNode,
        instruction: AbstractInsnNode
    ) {
        if (foundVideoUsage) {
            return
        }
        val owner = when (instruction) {
            is FieldInsnNode -> instruction.owner
            is MethodInsnNode -> instruction.owner
            else -> error("Only field or method accesses are supported")
        }
        if (owner.startsWith(JVM_MEDIASTORE_VIDEO)) {
            foundVideoUsage = true
            return
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf(NAME_VIDEO)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        // We already found a reference, no need to examine any more
        if (foundVideoUsage) {
            return
        }

        // Check if the "Video" reference is indeed the class we want:
        if (referenced is PsiClass && referenced.qualifiedName == FQN_MEDIASTORE_VIDEO) {
            foundVideoUsage = true
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!foundVideoUsage) {
            return
        }

        val mergedManifest = context.mainProject.mergedManifest ?: return
        val application = getFirstSubTagByName(
            mergedManifest.documentElement, TAG_APPLICATION
        ) ?: return

        val metadataElement =
            getSubTagsByName(application, TAG_META_DATA).find { element ->
                element.getAttributeNS(ANDROID_URI, ATTR_NAME) == VALUE_MEDIA_CAPABILITIES
            }
        if (metadataElement == null) {
            context.report(
                ISSUE,
                context.client.findManifestSourceLocation(application)
                    ?: Location.create(context.project.manifestFiles[0] ?: context.project.dir),
                "The app accesses `MediaStore.Video`, but is missing a `<meta-data>` tag " +
                    "with a `$VALUE_MEDIA_CAPABILITIES` declaration"
            )
        } else if (!metadataElement.hasAttributeNS(ANDROID_URI, ATTR_RESOURCE)) {
            context.report(
                ISSUE,
                context.client.findManifestSourceLocation(metadataElement)
                    ?: Location.create(context.project.manifestFiles[0] ?: context.project.dir),
                "The `$VALUE_MEDIA_CAPABILITIES` `<meta-data>` tag is missing the" +
                    " `android:resource` attribute pointing to a valid XML file"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MediaCapabilities",
            briefDescription = "Media Capabilities meta-data not specified",
            explanation =
                """
                In Android 12 and higher, an app that opens media files should explicitly specify \
                media formats that it doesn't support, so the OS can provide a transcoded file \
                instead.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                MediaCapabilitiesMetadataDetector::class.java,
                EnumSet.of(
                    Scope.MANIFEST,
                    Scope.JAVA_FILE,
                    Scope.JAVA_LIBRARIES
                ),
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_LIBRARIES)
            ),
            enabledByDefault = true
        )

        const val VALUE_MEDIA_CAPABILITIES = "android.content.MEDIA_CAPABILITIES"
        const val ATTR_RESOURCE = "resource"

        private const val FQN_MEDIASTORE = "android.provider.MediaStore"
        private const val NAME_VIDEO = "Video"
        private const val FQN_MEDIASTORE_VIDEO = "$FQN_MEDIASTORE.$NAME_VIDEO"
        private const val JVM_MEDIASTORE_VIDEO = "android/provider/MediaStore\$Video"
    }
}
