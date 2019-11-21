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
package com.android.tools.idea.wizard.template
import com.android.support.AndroidxNameUtils
import com.android.utils.usLocaleCapitalize
import com.google.common.base.CaseFormat
import com.google.common.base.Strings.emptyToNull
import com.android.tools.idea.wizard.template.AssetNameConverter.Type
import com.google.common.io.Resources
import java.io.File
import java.net.URL

data class GradleVersion(val major: Int, val minor: Int, val micro: Int) {
  operator fun compareTo(other: GradleVersion) = when {
    this == other -> 0
    this.major > other.major -> 1
    this.minor > other.minor -> 1
    this.micro > other.micro -> 1
    else -> -1
  }
}

// TODO(qumeric): make more reliable (add validation etc.)
private fun String.toGradleVersion(): GradleVersion {
  val (major, minor, micro) = this.split(".")
  return GradleVersion(major.toInt(), minor.toInt(), micro.toInt())
}

fun compareVersions(l: String, r: String): Int = l.toGradleVersion().compareTo(r.toGradleVersion())

/** Converts an Activity class name into a suitable layout name. */
fun activityToLayout(activityName: String, layoutName: String? = null): String =
  if (activityName.isNotEmpty())
    AssetNameConverter(Type.ACTIVITY, activityName)
      .overrideLayoutPrefix(layoutName)
      .getValue(Type.LAYOUT)
  else
    ""

/** Similar to [camelCaseToUnderlines], but strips off common class suffixes such as "Activity", "Fragment", etc. */
fun classToResource(name: String): String =
  if (name.isNotEmpty())
    AssetNameConverter(Type.CLASS_NAME, name).getValue(Type.RESOURCE)
  else ""

fun camelCaseToUnderlines(string: String): String = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, string)

fun underscoreToCamelCase(
  string: String
): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string)

fun escapeKotlinIdentifier(identifier: String): String =
  identifier.split(".").joinToString(".") { if (it in kotlinKeywords) "`$it`" else it }

/**
 * Creates a Java class name out of the given string, if possible.
 * For example, "My Project" becomes "MyProject", "hello" becomes "Hello", "Java's" becomes "Javas", and so on.
 *
 * @param string the string to be massaged into a Java class
 * @return the string as a Java class, or null if a class name could not be extracted
 */
fun extractClassName(string: String): String? {
  val javaIdentifier = string.dropWhile { !Character.isJavaIdentifierStart(it.toUpperCase()) }.filter(Character::isJavaIdentifierPart)
  return emptyToNull(javaIdentifier.usLocaleCapitalize())
}

fun layoutToActivity(name: String): String = AssetNameConverter(Type.LAYOUT, name).getValue(Type.ACTIVITY)
fun layoutToFragment(name: String): String = AssetNameConverter(Type.LAYOUT, name).getValue(Type.FRAGMENT)

fun getMaterialComponentName(oldName: String, useMaterial2: Boolean): String =
  if (useMaterial2) AndroidxNameUtils.getNewName(oldName) else oldName

// From https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
private val kotlinKeywords = listOf(
  "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for", "null", "true", "false", "is", "in",
  "throw", "return", "break", "continue", "object", "if", "try", "else", "while", "do", "when", "interface", "typeof")

fun underlinesToCamelCase(string: String): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string)

/**
 * Finds a resource given a file path and a context class. The implementation takes cares of the details of different file path
 * separators in different operating systems.
 */
fun findResource(contextClass: Class<Any>, from: File) : URL {
  // Windows file paths use '\', but resources are always '/', and they need to start at root.
  return Resources.getResource(contextClass, "/${from.path.replace('\\', '/')}")
}
