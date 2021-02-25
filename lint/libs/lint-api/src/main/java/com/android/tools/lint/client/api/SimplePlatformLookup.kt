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

package com.android.tools.lint.client.api

import com.android.SdkConstants.FD_ADDONS
import com.android.SdkConstants.FD_DATA
import com.android.SdkConstants.FD_PLATFORMS
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import com.android.SdkConstants.FN_SOURCE_PROP
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.OptionalLibrary
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.IOException
import java.nio.file.Path
import java.util.Properties

/**
 * Fast lookup of platform metadata.
 *
 * The purpose is to have a much faster implementation (which doesn't
 * look recursively at files or at a lot of other unrelated SDK
 * components), and doesn't have side effects such as writing metadata
 * files into the SDK folder.
 *
 * It serves a small subset of the full SDK manager:
 * <ul>
 * <li> Only supports Android platform components
 * <li> Only works for platforms 15 and later
 * <li> Only supports reading some platform metadata and looking up file
 *     locations (like android.jar), not downloading or
 *     installing files. [sdkHome] is the location of the SDK.
 * </ul>
 */
internal class SimplePlatformLookup(private val sdkHome: File) : PlatformLookup {
    /** All platforms discovered in [sdkHome] */
    private val targets: List<IAndroidTarget>

    init {
        val targets = mutableListOf<IAndroidTarget>()
        // Not limiting the platform search to $PLATFORM_HAS_PREFIX to deal
        // with simple renames and modified environments like g3
        addPlatforms(targets, sdkHome, FD_PLATFORMS, null)

        if (SUPPORTS_ADD_ONS) {
            addPlatforms(targets, sdkHome, FD_ADDONS, "addon-")
        }

        targets.sort()
        this.targets = targets
    }

    override fun getTargets(includeAddOns: Boolean): List<IAndroidTarget> {
        if (includeAddOns) {
            error("Add-ons not supported in this platform lookup")
        }
        return targets
    }

    override fun getTarget(buildTargetHash: String): IAndroidTarget? {
        return getTargets().firstOrNull { it.hashString() == buildTargetHash }
    }

    override fun getLatestSdkTarget(
        minApi: Int,
        includePreviews: Boolean,
        includeAddOns: Boolean
    ): IAndroidTarget? {
        if (includeAddOns) {
            error("Add-ons not supported in this platform lookup")
        }
        val latest = if (includePreviews) {
            targets.lastOrNull { it.isPlatform }
        } else {
            targets.lastOrNull { it.isPlatform && it.version.codename == null }
        } ?: return null
        return if (latest.version.apiLevel >= minApi) latest else null
    }

    /**
     * Looks up a file for for the platform with the given
     * [compileSdkPrefix], or null if the platform is unknown. The
     * specific file to be returned is specified by the given [pathId],
     * which is one of the path constants defined in [IAndroidTarget]
     */
    fun getFile(compileSdkPrefix: String, pathId: Int): File? {
        return getTarget(compileSdkPrefix)?.getPath(pathId)?.toFile()
    }

    companion object {
        /**
         * Skim through a platforms folder and adds any platforms found
         * within to the given list. This only works for directory
         * structures following the standard layout (the full SDK
         * manager allows more arbitrary renames of top level folders
         * etc; that's not supported here.)
         */
        private fun addPlatforms(
            into: MutableList<IAndroidTarget>,
            sdkHome: File,
            folder: String,
            prefix: String?
        ) {
            val platformFolders = File(sdkHome, folder).listFiles() ?: return
            for (platformFolder in platformFolders) {
                val name = platformFolder.name
                if ((prefix == null || name.startsWith(prefix) && !name.startsWith('.')) &&
                    platformFolder.isDirectory
                ) {
                    // LocalRepoLoaderImpl.PACKAGE_XML_FN
                    val packageXml = File(platformFolder, "package.xml")
                    if (packageXml.isFile) {
                        platformFromPackageXml(platformFolder, packageXml)?.let {
                            into.add(it)
                        }
                    } else {
                        val sourceProperties = File(platformFolder, FN_SOURCE_PROP)
                        if (sourceProperties.isFile) {
                            platformFromSourceProp(platformFolder, sourceProperties)?.let {
                                into.add(it)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Read in a [PlatformTarget] for a given SDK manager
         * package.xml file.
         */
        private fun platformFromPackageXml(location: File, packageXml: File): IAndroidTarget? {
            var buildTargetHash: String? = null
            var codeName: String? = null
            var apiLevel = -1
            var vendorId: String? = null
            var nameId: String? = null
            var revision = 1

            try {
                FileReader(packageXml).use {
                    val parser = KXmlParser()
                    parser.setInput(it)
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        val eventType = parser.eventType
                        if (eventType != XmlPullParser.START_TAG) continue
                        val depth = parser.depth
                        if (depth == 3) {
                            val tag = parser.name
                            if (tag == "revision") {
                                // <revision><major>4</major></revision>
                                if (parser.next() == XmlPullParser.START_TAG &&
                                    parser.name == "major" &&
                                    parser.next() == XmlPullParser.TEXT
                                ) {
                                    revision = parser.text.toIntOrNull() ?: 1
                                }
                            }
                        } else if (depth == 4) {
                            when (parser.name) {
                                "api-level" -> {
                                    if (parser.next() == XmlPullParser.TEXT) {
                                        val text = parser.text
                                        text.toIntOrNull()?.let { api -> apiLevel = api }
                                    }
                                }
                                "codename" -> {
                                    if (parser.next() == XmlPullParser.TEXT) {
                                        val text = parser.text
                                        if (text.isNotBlank()) {
                                            codeName = text
                                        }
                                    }
                                }
                                "vendor" -> {
                                    // <vendor><id>google</id>
                                    if (parser.next() == XmlPullParser.START_TAG &&
                                        parser.name == "id" &&
                                        parser.next() == XmlPullParser.TEXT
                                    ) {
                                        vendorId = parser.text
                                    }
                                }
                                "tag" -> {
                                    // <tag><id>google_apis</id>
                                    if (parser.next() == XmlPullParser.START_TAG &&
                                        parser.name == "id" &&
                                        parser.next() == XmlPullParser.TEXT
                                    ) {
                                        nameId = parser.text
                                    }
                                }
                            }
                        } else if (depth == 2) {
                            val tag = parser.name
                            if (tag == "localPackage") {
                                val path = parser.getAttributeValue(null, "path")
                                if (path != null && path.startsWith("platforms;")) {
                                    buildTargetHash = path.removePrefix("platforms;")
                                } // else -- add-on for example
                            }
                        }
                    }
                }
            } catch (ignore: Exception) {
            }

            var platform = true
            val version = AndroidVersion(apiLevel, codeName)
            if (nameId != null && vendorId != null && apiLevel != -1) {
                // Add-on
                buildTargetHash = AndroidTargetHash.getAddonHashString(
                    vendorId!!,
                    nameId!!,
                    version
                )
                platform = false
            }

            return if (buildTargetHash != null && apiLevel != -1) {
                PlatformTarget(
                    location,
                    buildTargetHash!!,
                    version,
                    revision,
                    platform
                )
            } else {
                null
            }
        }

        /**
         * Read in a [PlatformTarget] for a given SDK manager (older
         * format) source.properties file.
         */
        private fun platformFromSourceProp(
            location: File,
            sourceProperties: File
        ): IAndroidTarget? {
            try {
                FileInputStream(sourceProperties).use { input ->
                    val prop = Properties()
                    prop.load(input)

                    val platformVersion = prop.getProperty("Platform.Version")
                    val revision = prop.getProperty("Pkg.Revision")?.toIntOrNull() ?: 1
                    val apiLevel = prop.getProperty("AndroidVersion.ApiLevel")?.toInt()
                    val codeName = prop.getProperty("AndroidVersion.CodeName")
                    if (platformVersion != null && apiLevel != null) {
                        return PlatformTarget(
                            location,
                            sourceProperties.parentFile!!.name,
                            AndroidVersion(apiLevel, codeName),
                            revision,
                            true
                        )
                    }
                }
            } catch (ignore: IOException) {
            }
            return null
        }

        private fun getOptionalLibraries(platform: File): List<OptionalLibrary> {
            val optional = File(platform, "optional")
            val jsonFile = File(optional, "optional.json")
            if (!jsonFile.isFile) {
                return emptyList()
            }

            val libraries = mutableListOf<OptionalLibrary>()

            // We don't have access to a json parser and these regular files
            // under our control does not warrant adding it; unit tests verify
            // that it works with new platforms
            val json = jsonFile.readText()
            var index = 0
            fun nextItem(): Boolean {
                index = json.indexOf('{', index)
                return index != -1
            }

            fun nextToken(): Any? {
                while (index < json.length) {
                    val c = json[index]
                    if (c.isWhitespace() || c == '{' || c == '[') {
                        index++
                    } else if (c == '}' || c == ']') {
                        return null
                    } else {
                        break
                    }
                }
                if (index == json.length) {
                    return null
                }
                val start = index
                val buffer = StringBuilder()
                if (json[start] == '"') {
                    index++
                    while (index < json.length) {
                        val c = json[index++]
                        if (c == '\\') {
                            buffer.append(json[index++])
                            continue
                        }
                        if (c == '"') {
                            index++
                            return buffer.toString()
                        }
                        buffer.append(c)
                    }
                    return null
                } else {
                    var end = index + 1
                    while (index < json.length) {
                        val c = json[index++]
                        if (c.isWhitespace() || c == '{' || c == '[') {
                            end = index - 1
                            break
                        } else if (c == '}' || c == ']') {
                            return null
                        }
                    }
                    if (index == json.length) {
                        return null
                    }
                    val valueString = json.substring(start, end)
                    if (json[start].isDigit()) {
                        // Number
                        val dot = json.indexOf('.', start)
                        return if (dot != -1 && dot < end) {
                            valueString.toDoubleOrNull()
                        } else {
                            valueString.toIntOrNull()
                        }
                    } else {
                        // Boolean
                        if (valueString == VALUE_TRUE) {
                            return true
                        } else if (valueString == VALUE_FALSE) {
                            return false
                        }
                    }
                }
                return null
            }

            fun nextPair(): Pair<String, Any>? {
                val keyToken = nextToken()
                val valueToken = nextToken()
                return if (keyToken is String && valueToken != null) {
                    Pair(keyToken, valueToken)
                } else {
                    null
                }
            }

            while (true) {
                if (!nextItem()) {
                    break
                }
                var name: String? = null
                var jar: String? = null
                var manifest = false
                while (true) {
                    val pair = nextPair() ?: break
                    val key = pair.first
                    val value = pair.second
                    if (key == "name" && value is String) {
                        name = value
                    } else if (key == "jar" && value is String) {
                        jar = value
                    } else if (key == "manifest" && value is Boolean) {
                        manifest = value
                    }
                }
                if (name != null && jar != null) {
                    val jarPath = File(optional, jar).toPath()
                    val library = object : OptionalLibrary {
                        override fun getName(): String = name
                        override fun getJar(): Path = jarPath
                        override fun getDescription(): String = name
                        override fun isManifestEntryRequired(): Boolean = manifest
                        override fun getLocalJarPath(): String = getJar().fileName.toString()
                    }
                    libraries.add(library)
                }
            }

            return libraries
        }
    }

    /**
     * Represents a single Android platform installed in the SDK folder;
     * the [location] is the platforms/ folder containing all the
     * platform metadata; the [buildTargetHash] is the string which
     * corresponds to the compileSdkVersion as a string (e.g. if you
     * specify 30 it will be interpreted as "android-30", and if you
     * specify "android-R" it will be used as is). The [version] is
     * the API level and optionally code name for previews. For normal
     * SDK releases, [platform] is true, and for add-ons it's false.
     */
    private class PlatformTarget(
        val location: File,
        val buildTargetHash: String,
        private val version: AndroidVersion,
        private val revision: Int,
        private val platform: Boolean
    ) : IAndroidTarget, Comparable<IAndroidTarget> {
        override fun isPlatform(): Boolean = platform
        override fun hashString(): String = buildTargetHash
        override fun getVersion(): AndroidVersion = version
        override fun getLocation(): String = location.path
        override fun getPath(pathId: Int): Path = getFile(pathId).toPath()

        /**
         * Looks up a file for this platform by the given
         * [pathId], which is one of the path constants defined in
         * [IAndroidTarget]
         */
        fun getFile(pathId: Int): File {
            return when (pathId) {
                IAndroidTarget.ANDROID_JAR -> File(location, FN_FRAMEWORK_LIBRARY)
                IAndroidTarget.DATA -> File(location, FD_DATA)
                IAndroidTarget.RESOURCES -> File(location, FD_DATA + File.separator + FD_RES)
                IAndroidTarget.ATTRIBUTES -> File(location, "data/res/values/attrs.xml")
                else -> error("Unsupported path id in ${SimplePlatformLookup::class.java.name}")
            }
        }

        override fun getRevision(): Int = revision

        private var optionalLibraries: List<OptionalLibrary>? = null

        override fun getOptionalLibraries(): List<OptionalLibrary> {
            return optionalLibraries
                ?: run {
                    Companion.getOptionalLibraries(location)
                        .also { optionalLibraries = it }
                }
        }

        // Sort in ascending order
        override operator fun compareTo(other: IAndroidTarget): Int {
            val versionDelta = version.compareTo(other.version)
            if (versionDelta != 0) {
                return versionDelta
            }
            val typeDelta = (if (isPlatform) 1 else 0) - (if (other.isPlatform) 1 else 0)
            if (typeDelta != 0) {
                return typeDelta
            }
            // Duplicated platforms for a version and type -- unexpected, but fall back
            // to alphabetical sort on location
            return location.path.compareTo(other.location)
        }

        override fun toString(): String {
            return "${if (platform) "Platform" else "Add-on"} $buildTargetHash: version=$version"
        }

        override fun getVendor(): String = unsupported()
        override fun getName(): String = unsupported()
        override fun getFullName(): String = unsupported()
        override fun getClasspathName(): String = unsupported()
        override fun getShortClasspathName(): String = unsupported()
        override fun getVersionName(): String = unsupported()
        override fun getParent(): IAndroidTarget = unsupported()
        override fun getBuildToolInfo(): BuildToolInfo = unsupported()
        override fun getBootClasspath(): List<String> = unsupported()
        override fun hasRenderingLibrary(): Boolean = unsupported()
        override fun getSkins(): Array<Path> = unsupported()
        override fun getDefaultSkin(): Path = unsupported()
        override fun getPlatformLibraries(): Array<String> = unsupported()
        override fun getProperty(name: String?): String = unsupported()
        override fun getProperties(): Map<String, String> = unsupported()
        override fun canRunOn(target: IAndroidTarget?): Boolean = unsupported()
        override fun getAdditionalLibraries(): MutableList<OptionalLibrary> = unsupported()

        private fun unsupported(): Nothing =
            error("This operation is not supported on light weight IAndroidTargets")
    }
}
