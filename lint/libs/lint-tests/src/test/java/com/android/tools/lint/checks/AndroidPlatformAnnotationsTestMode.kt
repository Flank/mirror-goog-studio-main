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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.checks.AbstractCheckTest.SUPPORT_ANNOTATIONS_JAR
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.ANDROIDX_ANNOTATIONS_PREFIX
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.PLATFORM_ANNOTATIONS_PREFIX
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.TypePath
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.attribute.FileTime
import java.util.jar.JarEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal class AndroidPlatformAnnotationsTestMode : TestMode(
    description = "Platform Annotations",
    "AbstractCheckTest.PLATFORM_ANNOTATIONS_TEST_MODE"
) {
    override val folderName: String = "platform-annotations"
    override val modifiesSources: Boolean = true

    private fun applies(file: TestFile): Boolean {
        if (!(file.targetRelativePath.endsWith(DOT_KT) || file.targetRelativePath.endsWith(DOT_JAVA))) {
            return false
        }
        val source = file.contents
        if (!source.contains(ANDROIDX_ANNOTATIONS_PREFIX)) {
            return false
        }
        var offset = 0
        while (true) {
            offset = source.indexOf(ANDROIDX_ANNOTATIONS_PREFIX, offset)
            if (offset == -1) {
                return true
            }
            offset += ANDROIDX_ANNOTATIONS_PREFIX.length

            // Check for AndroidX annotations that are not in the platform, so this test
            // is not relevant

            // The following annotations are currently in the platform but not AndroidX:
            // AppIdInt, BroadcastBehavior, BytesLong, CallbackExecutor, Condemned,
            // CurrentTimeMillisLong, CurrentTimeSecondsLong, DurationMillisLong,
            // ElapsedRealtimeLong, Hide, O, SdkConstant, SuppressAutoDoc,
            // SuppressLint, SystemApi, SystemService, TargetApi, TestApi,
            // UserHandleAware, UserIdInt, Widget
            //
            // The following annotations are currently in AndroidX but not the platform:
            // ChecksSdkIntAtLeast, ContentView, DimensionUnit, DisplayContext.,
            // DoNotInline, GravityInt, GuardedBy, InspectableProperty, Keep,
            // NonUiContext, ProductionVisibility, RequiresApi, RestrictTo, UiContext,
            // VisibleForTesting

            if (source.startsWith("RequiresApi", offset) ||
                source.startsWith("Keep", offset) ||
                source.startsWith("ChecksSdkIntAtLeast", offset) ||
                source.startsWith("RestrictTo", offset) ||
                source.startsWith("VisibleForTesting", offset)
            ) {
                return false
            }
        }
    }

    override fun applies(context: TestModeContext): Boolean {
        return context.projects.any { project ->
            project.files.any { it == SUPPORT_ANNOTATIONS_JAR } &&
                project.files.any { file -> applies(file) }
        }
    }

    private fun replaceAnnotationSource(file: File): Boolean {
        val source = file.readText()
        val rewritten = source.replace(ANDROIDX_ANNOTATIONS_PREFIX, PLATFORM_ANNOTATIONS_PREFIX)
        if (rewritten != source) {
            file.writeText(rewritten)
            return true
        }
        return false
    }

    override fun before(context: TestModeContext): Any? {
        val projectFolders = context.projectFolders

        var unchanged = true
        projectFolders.forEach { root ->
            root.walk()
                .filter { it.isFile && (it.path.endsWith(DOT_JAVA) || it.path.endsWith(DOT_KT)) }
                .forEach {
                    if (replaceAnnotationSource(it)) {
                        unchanged = false
                    }
                }

            val annotationsJar = File(root, SUPPORT_ANNOTATIONS_JAR.targetRelativePath)
            if (annotationsJar.exists()) {
                val rewritten = rewriteAnnotationJar(annotationsJar.readBytes())
                annotationsJar.writeBytes(rewritten)
            }
        }

        return if (unchanged) CANCEL else null
    }

    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        This test mode checks tests that
        contain annotation references that they also work with platform
        annotations.
        """.trimIndent()

    private fun rewriteAnnotationClass(bytes: ByteArray, path: String): ByteArray {
        return try {
            val reader = ClassReader(bytes)
            rewriteOuterClass(reader)
        } catch (ioe: IOException) {
            error("Could not process " + path + ": " + ioe.localizedMessage)
        }
    }

    private fun mapName(name: String): String {
        return name.replace("androidx/annotation/", "android/annotation/")
    }

    private fun rewriteOuterClass(reader: ClassReader): ByteArray {
        val classWriter = ClassWriter(ASM9)
        val classVisitor = object : ClassVisitor(ASM9, classWriter) {
            override fun visit(
                version: Int,
                access: Int,
                name: String,
                signature: String?,
                superName: String?,
                interfaces: Array<out String>?
            ) {
                super.visit(version, access, mapName(name), signature, superName, interfaces)
            }

            override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
                super.visitInnerClass(name?.let { mapName(it) }, outerName?.let { mapName(it) }, innerName, access)
            }

            override fun visitField(
                access: Int,
                name: String,
                descriptor: String?,
                signature: String?,
                value: Any?
            ): FieldVisitor {
                return super.visitField(access, name, descriptor?.let { mapName(it) }, signature, value)
            }

            override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
                super.visitOuterClass(owner, name, descriptor)
            }

            override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                descriptor: String?,
                visible: Boolean
            ): AnnotationVisitor {
                return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
            }

            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
                return super.visitAnnotation(descriptor, visible)
            }

            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                return super.visitMethod(access, name, descriptor?.let { mapName(it) }, signature, exceptions)
            }
        }

        reader.accept(classVisitor, 0)
        return classWriter.toByteArray()
    }

    private fun rewriteAnnotationJar(jar: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(jar)
        val bos = ByteArrayOutputStream()
        val zos = ZipOutputStream(bos)
        val zis = ZipInputStream(bis)
        val zeroTime = FileTime.fromMillis(0)

        while (true) {
            val entry = zis.nextEntry ?: break
            val originalName = entry.name
            val name = mapName(originalName)

            // Preserve the STORED method of the input entry.
            val newEntry: JarEntry = if (entry.method == JarEntry.STORED) {
                val jarEntry = JarEntry(entry)
                jarEntry.size = entry.size
                jarEntry.compressedSize = entry.compressedSize
                jarEntry.crc = entry.crc
                jarEntry
            } else {
                // Create a new entry so that the compressed len is recomputed.
                JarEntry(name)
            }

            newEntry.lastAccessTime = zeroTime
            newEntry.creationTime = zeroTime
            newEntry.lastModifiedTime = entry.lastModifiedTime

            zos.putNextEntry(newEntry)

            if (name.endsWith(SdkConstants.DOT_CLASS) && name != originalName && !entry.isDirectory) {
                val bytes = zis.readBytes()
                val rewritten = rewriteAnnotationClass(bytes, name)
                zos.write(rewritten)
            } else {
                zis.copyTo(zos)
            }

            zos.closeEntry()
            zis.closeEntry()
        }

        zis.close()
        zos.close()

        return bos.toByteArray()
    }
}
