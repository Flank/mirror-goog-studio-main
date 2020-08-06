/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_PERMISSION
import com.android.testutils.TestUtils
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.android.tools.lint.checks.ApiLookup.get
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.utils.XmlUtils
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import com.google.common.base.Joiner
import com.google.common.io.ByteStreams
import org.junit.Assert.fail
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.net.URLClassLoader
import java.util.HashMap
import kotlin.text.Charsets.UTF_8

data class Permission(
    /** Permission name */
    val name: String,
    /** Manifest.permission class field name */
    val field: String?,
    /** API level this permission was introduced in */
    val introducedIn: Int
) {

    /** Bit mask for API levels where this permission was marked dangerous */
    private var dangerous: Long = 0
    /** Bit mask for API levels where this permission was marked signature/system */
    private var signature: Long = 0

    /** API level this permission was made a signature permission in, or 0 */
    val signatureIn: Int get() = signatureFrom()

    /** Last API level this permission was a signature permission in, or 0 */
    val signatureOut: Int get() = signatureUntil()

    /** API level this permission was made a dangerous permission in, or 0 */
    val dangerousIn: Int get() = dangerousFrom()

    /** Last API level this permission was a dangerous permission in, or 0 */
    val dangerousOut: Int get() = dangerousUntil()

    /* Mark this permission as dangerous in the given API level */
    fun markDangerous(apiLevel: Int) {
        dangerous = dangerous or ((1 shl apiLevel).toLong())
    }

    /* Mark this permission as signature/system in the given API level */
    fun markSignature(apiLevel: Int) {
        signature = signature or ((1 shl apiLevel).toLong())
    }

    /** Returns the first API level this permission is dangerous in, if any */
    fun dangerousFrom(): Int = lowestBit(dangerous)

    /** Returns the last API level this permission is dangerous in, if any */
    fun dangerousUntil(): Int = highestBit(dangerous)

    /** Returns the first API level this permission is dangerous in, if any */
    fun signatureFrom(): Int = lowestBit(signature)

    /** Returns the last API level this permission is dangerous in, if any */
    fun signatureUntil(): Int = highestBit(signature)

    override fun toString(): String {
        return "Permission($name, from=$introducedIn, dangerous=${getApiLevels(dangerous)}, signature=${getApiLevels(
            signature
        )})"
    }

    private fun lowestBit(mask: Long): Int {
        if (mask == 0L) {
            return 0
        }
        var apiLevel = 0
        var current = mask
        while (current and 1L == 0L) {
            current = current ushr 1
            apiLevel++
        }

        return apiLevel
    }

    private fun highestBit(mask: Long): Int {
        if (mask == 0L) {
            return 0
        }
        var apiLevel = 0
        var current = mask
        var max = 0
        while (current != 0L) {
            if (current and 1L == 1L) {
                max = apiLevel
            }
            current = current ushr 1
            apiLevel++
        }

        return max
    }

    private fun getApiLevels(mask: Long): List<Int> {
        if (mask == 0L) {
            return emptyList()
        }
        val list = mutableListOf<Int>()
        var apiLevel = 0
        var current = mask
        while (current != 0L) {
            if (current and 1L == 1L) {
                list.add(apiLevel)
            }
            current = current ushr 1
            apiLevel++
        }

        return list
    }
}

/**
 * Analyzes the SDK to extract permission data used by various lint
 * checks such as the [com.android.tools.lint.checks.SystemPermissionsDetector]
 * and the [com.android.tools.lint.checks.PermissionDetector]
 */
class PermissionDataGenerator {
    val permissions = computePermissions(skipHidden = false)
    var maxApiLevel: Int = 0

    fun getDangerousPermissions(skipHidden: Boolean = true, minApiLevel: Int): List<Permission> {
        return permissions?.filter { permission ->
            permission.dangerousIn > 0 &&
                permission.dangerousOut >= minApiLevel &&
                (!skipHidden || permission.field != null)
        }?.toList() ?: emptyList()
    }

    fun getSignaturePermissions(skipHidden: Boolean = false): List<Permission> {
        return permissions?.filter {
            it.signatureIn > 0 && (!skipHidden || it.field != null)
        }?.toList() ?: emptyList()
    }

    /**
     * Returns permissions that were added as signature permissions later (not at
     * the API level they were initially introduced. This only considers non-hidden
     * permissions.
     */
    fun getPermissionsMarkedAsSignatureLater(): List<Permission> {
        permissions ?: return emptyList()

        return permissions.filter { permission ->
            permission.introducedIn < permission.signatureIn && permission.field != null
        }.sortedWith(compareBy(Permission::signatureIn, Permission::name))
    }

    fun getLastNonSignatureApiLevelSwitch(): String {
        // Generate exception maps
        val sb = StringBuilder()
        sb.append("private static int getLastNonSignatureApiLevel(@NonNull String name) {\n")
        sb.append("    switch (name) {\n")
        for (permission in getPermissionsMarkedAsSignatureLater()) {
            // Ignore really old news
            if (permission.signatureIn < 15) {
                continue
            }
            sb.append("        case \"").append(permission.name).append("\": return ")
                .append(permission.signatureIn - 1).append(";\n")
        }
        sb.append("        default: return -1;\n")
        sb.append("    }\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun getRemovedSignaturePermissions(): List<Permission> {
        permissions ?: return emptyList()

        return permissions.filter { permission ->
            val lastSignatureApiLevel = permission.signatureOut
            permission.field != null && lastSignatureApiLevel > 1 && lastSignatureApiLevel < maxApiLevel
        }.sortedWith(compareBy(Permission::signatureIn, Permission::name))
    }

    fun getRemovedSignaturePermissionSwitch(): String {
        val sb = StringBuilder()
        sb.append("    switch (name) {\n")
        for (permission in getRemovedSignaturePermissions()) {
            // Ignore really old news
            if (permission.signatureOut < 15) {
                continue
            }
            sb.append("        case \"").append(permission.name).append("\": return ")
                .append(permission.signatureOut).append(";\n")
        }
        sb.append("        default: return -1;\n")
        sb.append("    }\n")
        return sb.toString()
    }

    fun getPermissionsMarkedAsDangerousLater(): List<Permission> {
        permissions ?: return emptyList()

        return permissions.filter { permission ->
            permission.introducedIn < permission.dangerousIn && permission.field != null
        }.sortedWith(compareBy(Permission::dangerousIn, Permission::name))
    }

    fun getLastNonDangerousApiLevelSwitch(): String {
        // Generate exception maps
        val sb = StringBuilder()
        sb.append("private static int getLastNonDangerousApiLevel(@NonNull String name) {\n")
        sb.append("    switch (name) {\n")
        for (permission in getPermissionsMarkedAsDangerousLater()) {
            // Ignore really old news
            if (permission.dangerousIn < 15) {
                continue
            }
            sb.append("        case \"").append(permission.name).append("\": return ")
                .append(permission.dangerousIn - 1).append(";\n")
        }
        sb.append("        default: return -1;\n")
        sb.append("    }\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun getRemovedDangerousPermissions(): List<Permission> {
        permissions ?: return emptyList()

        return permissions.filter { permission ->
            val lastDangerousApiLevel = permission.dangerousOut
            permission.field != null && lastDangerousApiLevel > 1 && lastDangerousApiLevel < maxApiLevel
        }.sortedWith(compareBy(Permission::dangerousOut, Permission::name))
    }

    fun getRemovedDangerousPermissionsSwitch(): String {
        val sb = StringBuilder()
        sb.append("    switch (name) {\n")
        for (permission in getRemovedDangerousPermissions()) {
            // Ignore really old news
            if (permission.dangerousIn < 15) {
                continue
            }
            sb.append("        case \"").append(permission.name).append("\": return ")
                .append(permission.dangerousOut).append(";\n")
        }
        sb.append("        default: return -1;\n")
        sb.append("    }\n")
        return sb.toString()
    }

    fun getPermissionNames(permissions: List<Permission>): Array<String> {
        return permissions.map { it.name }.sortedBy { it }.toTypedArray()
    }

    fun assertSamePermissions(expected: Array<String>, actual: Array<String>) {
        if (!actual.contentEquals(expected)) {
            println("Correct list of permissions:")
            for (name in actual) {
                println("            \"$name\",")
            }
            fail(
                "List of revocable permissions has changed:\n" +
                    // Make the diff show what it take to bring the actual results into the
                    // expected results
                    TestUtils.getDiff(
                        Joiner.on('\n').join(expected),
                        Joiner.on('\n').join(actual)
                    )
            )
        }
    }

    private fun computePermissions(skipHidden: Boolean = false): List<Permission>? {
        val top = System.getenv("ANDROID_BUILD_TOP") ?: return null

        val apiLookup = getApiLookup() ?: return null

        val nameToPermission = HashMap<String, Permission>()

        var apiLevel = 1
        while (true) {
            val jar = findSdkJar(top, apiLevel) ?: break
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()))
            val valueToFieldName = computeFieldToPermissionNameMap(loader)
            val document = getManifestDocument(loader)
            if (document != null) {
                var element = getFirstSubTagByName(document.documentElement, TAG_PERMISSION)
                while (element != null) {
                    processPermissionTag(
                        element,
                        apiLevel,
                        nameToPermission,
                        valueToFieldName,
                        skipHidden,
                        apiLookup
                    )
                    element = getNextTagByName(element, TAG_PERMISSION)
                }
            }
            apiLevel++
        }

        maxApiLevel = apiLevel - 1
        return nameToPermission.values.sortedBy { it.name }.toList()
    }

    private fun isDangerousPermission(
        protectionLevels: List<String>,
        name: String,
        apiLevel: Int
    ): Boolean {
        if (apiLevel >= 23 && name == "android.permission.GET_ACCOUNTS") {
            // No longer needed in M. See issue 223244.
            return false
        }

        return protectionLevels.contains("dangerous")
    }

    private fun isSignaturePermission(protectionLevels: List<String>): Boolean =
        protectionLevels.contains("signature") ||
            protectionLevels.contains("privileged") ||
            protectionLevels.contains("signatureOrSystem")

    private fun processPermissionTag(
        element: Element,
        apiLevel: Int,
        nameToPermission: MutableMap<String, Permission>,
        valueToFieldName: Map<String, String>,
        skipHidden: Boolean,
        apiLookup: ApiLookup
    ) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name.isEmpty()) {
            return
        }

        val field = valueToFieldName[name]
        if (skipHidden && field == null) {
            return
        }

        val protectionLevels = getProtectionLevels(element)
        val signaturePermission = isSignaturePermission(protectionLevels)
        val dangerousPermission = isDangerousPermission(protectionLevels, name, apiLevel)

        if (dangerousPermission || signaturePermission) {
            val permission = nameToPermission[name] ?: run {
                val fieldVersion = if (field != null) apiLookup.getFieldVersion(
                    "android/Manifest\$permission", field
                ) else -1

                val new = Permission(name, field, fieldVersion)
                nameToPermission.put(name, new)
                new
            }

            if (signaturePermission) {
                permission.markSignature(apiLevel)
            }

            if (dangerousPermission) {
                permission.markDangerous(apiLevel)
            }
        }
    }

    private fun getProtectionLevels(element: Element): List<String> {
        val attribute = element.getAttributeNS(ANDROID_URI, "protectionLevel")
        val protectionLevel = if (attribute.isEmpty()) {
            "0"
        } else {
            attribute
        }
        if (Character.isDigit(protectionLevel[0])) {
            val protectionLevels = mutableListOf<String>()
            val protectionLevelInt = Integer.decode(protectionLevel)
            // See res/values/attrs_manifest.xml for flag declarations
            if (flagSet(protectionLevelInt, 2)) {
                protectionLevels.add("signature")
            }
            if (flagSet(protectionLevelInt, 3)) {
                protectionLevels.add("signatureOrSystem")
            } else if (flagSet(protectionLevelInt, 1)) {
                protectionLevels.add("dangerous")
            }
            if (flagSet(protectionLevelInt, 0x10)) {
                protectionLevels.add("privileged")
            }
            if (flagSet(protectionLevelInt, 0x20)) {
                protectionLevels.add("development")
            }
            if (flagSet(protectionLevelInt, 0x40)) {
                protectionLevels.add("appop")
            }
            if (flagSet(protectionLevelInt, 0x80)) {
                protectionLevels.add("pre23")
            }
            if (flagSet(protectionLevelInt, 0x100)) {
                protectionLevels.add("verifier")
            }
            if (flagSet(protectionLevelInt, 0x400)) {
                protectionLevels.add("preinstalled")
            }
            if (flagSet(protectionLevelInt, 0x800)) {
                protectionLevels.add("setup")
            }
            if (flagSet(protectionLevelInt, 0x1000)) {
                protectionLevels.add("instant")
            }
            if (flagSet(protectionLevelInt, 0x2000)) {
                protectionLevels.add("runtime")
            }
            return protectionLevels
        } else {
            // Already a string (probably parsing source level XML)
            return protectionLevel.split("\\|").toList()
        }
    }

    private fun flagSet(protectionLevelInt: Int, flag: Int): Boolean {
        return protectionLevelInt and flag == flag
    }

    /** Returns the android.jar file for the given API level, or null if not found/valid */
    private fun findSdkJar(top: String, apiLevel: Int): File? {
        var jar = File(top, "prebuilts/sdk/$apiLevel/current/android.jar")
        if (!jar.exists()) {
            jar = File(
                top, // API levels 1, 2 and 3
                "prebuilts/tools/common/api-versions/android-$apiLevel/android.jar"
            )
            if (!jar.exists()) {
                if (apiLevel < 25) {
                    System.err.println("Expected to find all the jar files here")
                }
                return null
            }
        }

        return jar
    }

    private fun getApiLookup(): ApiLookup? {
        return get(object : TestLintClient() {
            override fun getSdkHome(): File? {
                return TestUtils.getSdk()
            }
        }) ?: run {
            println("Couldn't find API database")
            return null
        }
    }

    /**
     * Finds the binary AndroidManifest.xml in the given compiled SDK jar and
     * returns the DOM document of a pretty printed version of it
     */
    private fun getManifestDocument(loader: URLClassLoader): Document? {
        val stream = loader.getResourceAsStream("AndroidManifest.xml")
        val bytes = ByteStreams.toByteArray(stream)
        stream.close()
        val xml = String(BinaryXmlParser.decodeXml("AndroidManifest.xml", bytes), UTF_8)
        return XmlUtils.parseDocumentSilently(xml, true)
    }

    /** Computes map from permission name to field in Manifest.permission */
    private fun computeFieldToPermissionNameMap(loader: URLClassLoader): HashMap<String, String> {
        val clz = Class.forName("android.Manifest\$permission", true, loader)
        val valueToFieldName = HashMap<String, String>()
        for (field in clz.declaredFields) {
            val initial = field.get(null)
            if (initial is String) {
                val fieldName = field.name
                valueToFieldName.put(initial, fieldName)
            }
        }
        return valueToFieldName
    }
}
