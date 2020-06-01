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

package com.android.build.gradle.internal.res.shrinker

import com.android.SdkConstants.DOT_9PNG
import com.android.SdkConstants.DOT_PNG
import com.android.SdkConstants.DOT_XML
import com.android.apksig.internal.util.ByteStreams
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_9PNG
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_9PNG_CRC
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_BINARY_XML
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_BINARY_XML_CRC
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PNG
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PNG_CRC
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PROTO_XML
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PROTO_XML_CRC
import com.android.build.gradle.internal.res.shrinker.gatherer.ResourcesGatherer
import com.android.build.gradle.internal.res.shrinker.graph.ResourcesGraphBuilder
import com.android.build.gradle.internal.res.shrinker.obfuscation.ObfuscationMappingsRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ResourceUsageRecorder
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.google.common.base.Preconditions
import com.google.common.io.Files
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Unit that analyzes all resources (after resource merging, compilation and code shrinking has
 * been completed) and figures out which resources are unused, and replaces them with dummy content
 * inside zip archive file.
 *
 * Resource shrinker implementation that allows to customize:
 * <ul>
 *     <li>application resource gatherer (from R files, resource tables, etc);
 *     <li>recorder for mappings from obfuscated class/methods to original class/methods;
 *     <li>sources from which resource usages are recorded (Dex files, compiled JVM classes,
 *         AndroidManifests, etc);
 *     <li>resources graph builder that connects resources dependent on each other (analyzing
 *         raw resources content in XML, HTML, CSS, JS, analyzing resource content in proto
 *         compiled format);
 * </ul>
 */
class ResourceShrinkerImpl(
    private val resourcesGatherer: ResourcesGatherer,
    private val obfuscationMappingsRecorder: ObfuscationMappingsRecorder?,
    private val usageRecorders: List<ResourceUsageRecorder>,
    private val graphBuilder: ResourcesGraphBuilder,
    private val debugReporter: ShrinkerDebugReporter,
    private val apkFormat: ApkFormat
) : ResourceShrinker {
    val model: ResourceShrinkerModel = ResourceShrinkerModel(debugReporter)
    private lateinit var unused: List<Resource>

    override fun analyze() {
        resourcesGatherer.gatherResourceValues(model)
        obfuscationMappingsRecorder?.recordObfuscationMappings(model)
        usageRecorders.forEach { it.recordUsages(model) }
        graphBuilder.buildGraph(model)

        model.usageModel.processToolsAttributes()
        model.keepPossiblyReferencedResources()

        debugReporter.debug { model.usageModel.dumpResourceModel() }

        unused = model.usageModel.findUnused()
    }

    override fun close() {
        debugReporter.close()
    }

    override fun getUnusedResourceCount(): Int {
        return unused.size
    }

    override fun rewriteResourcesInApkFormat(source: File, dest: File) {
        rewriteResourceZip(source, dest, "")
    }

    fun rewriteResourceZip(source: File, dest: File, modulePrefix: String) {
        if (dest.exists() && !dest.delete()) {
            throw IOException("Could not delete $dest")
        }
        ZipFile(source).use { zip ->
            JarOutputStream(BufferedOutputStream(FileOutputStream(dest))).use { zos ->
                // Rather than using Deflater.DEFAULT_COMPRESSION we use 9 here,  since that seems
                // to match the compressed sizes we observe in source .ap_ files encountered by the
                // resource shrinker:
                zos.setLevel(9)

                zip.entries().asSequence().forEach {
                    if (shouldBeReplacedWithDummy(it, modulePrefix)) {
                        replaceWithDummyEntry(zos, it)
                    } else {
                        copyToOutput(zip.getInputStream(it), zos, it)
                    }
                }
            }
        }
        // If net negative, copy original back. This is unusual, but can happen
        // in some circumstances, such as the one described in
        // https://plus.google.com/+SaidTahsinDane/posts/X9sTSwoVUhB
        // "Removed unused resources: Binary resource data reduced from 588KB to 595KB: Removed -1%"
        // Guard against that, and worst case, just use the original.
        val before = source.length()
        val after = dest.length()
        if (after > before) {
            debugReporter.info {
                "Resource shrinking did not work (grew from $before to $after); using original " +
                        "instead"
            }
            Files.copy(source, dest)
        }
    }

    private fun shouldBeReplacedWithDummy(entry: ZipEntry, modulePrefix: String): Boolean {
        val resPath = when  {
            modulePrefix.isEmpty() -> "res/"
            else -> "$modulePrefix/res/"
        }
        if (entry.isDirectory || !entry.name.startsWith(resPath)) {
            return false
        }
        val (folderName, fileName) = entry.name.substring(resPath.length).split("/", limit = 2)
        val resource = model.usageModel.getResourceByJarPath(folderName, fileName)
        Preconditions.checkNotNull(
            resource,
            "Resource for entry '" + entry.name + "' was not gathered."
        )
        return !resource!!.isReachable
    }

    /** Replaces the given entry with a minimal valid file of that type.  */
    private fun replaceWithDummyEntry(zos: JarOutputStream, entry: ZipEntry) {
        // Create a new entry so that the compressed len is recomputed.
        val name = entry.name
        val (bytes, crc) = when {
            name.endsWith(DOT_9PNG) -> TINY_9PNG to TINY_9PNG_CRC
            name.endsWith(DOT_PNG) -> TINY_PNG to TINY_PNG_CRC
            name.endsWith(DOT_XML) && apkFormat == ApkFormat.BINARY ->
                TINY_BINARY_XML to TINY_BINARY_XML_CRC
            name.endsWith(DOT_XML) && apkFormat == ApkFormat.PROTO ->
                TINY_PROTO_XML to TINY_PROTO_XML_CRC
            else -> ByteArray(0) to 0L
        }

        val outEntry = JarEntry(name)
        if (entry.time != -1L) {
            outEntry.time = entry.time
        }
        if (entry.method == JarEntry.STORED) {
            outEntry.method = JarEntry.STORED
            outEntry.size = bytes.size.toLong()
            outEntry.crc = crc
        }
        zos.putNextEntry(outEntry)
        zos.write(bytes)
        zos.closeEntry()
        debugReporter.info {
            "Skipped unused resource $name: ${entry.size} bytes (replaced with small dummy file " +
                    "of size ${bytes.size} bytes)"
        }
    }

    private fun copyToOutput(zis: InputStream, zos: JarOutputStream, entry: ZipEntry) {
        // We can't just compress all files; files that are not compressed in the source .ap_ file
        // must be left uncompressed here, since for example RAW files need to remain uncompressed
        // in the APK such that they can be mmap'ed at runtime.
        // Preserve the STORED method of the input entry.
        val outEntry = when (entry.method) {
            JarEntry.STORED -> JarEntry(entry)
            else -> JarEntry(entry.name)
        }
        if (entry.time != -1L) {
            outEntry.time = entry.time
        }
        zos.putNextEntry(outEntry)
        if (!entry.isDirectory) {
            zos.write(ByteStreams.toByteArray(zis))
        }
        zos.closeEntry()
    }
}

private fun ResourceUsageModel.getResourceByJarPath(folder: String, name: String): Resource? {
    val folderType = ResourceFolderType.getFolderType(folder) ?: return null
    val resourceName = name.substringBefore('.')
    return FolderTypeRelationship.getRelatedResourceTypes(folderType)
        .filterNot { it == ResourceType.ID }
        .map { getResource(it, resourceName) }
        .filterNotNull()
        .firstOrNull()
}