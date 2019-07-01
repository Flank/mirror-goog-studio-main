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

import com.android.utils.usLocaleCapitalize
import com.google.common.base.CaseFormat
import com.google.common.base.Strings.emptyToNull

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

fun camelCaseToUnderlines(string: String): String = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, string)

fun underlinesToCamelCase(string: String): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string)
