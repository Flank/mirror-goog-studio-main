/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.manifmerger.testutils

fun manifest(attr_package: String, includeXmlnsTools: Boolean = false, init: Manifest.() -> Unit) =
    Manifest(attr_package, includeXmlnsTools).apply(init)

class Manifest(attr_package: String, includeXmlnsTools: Boolean) : Tag("manifest") {
    init {
        attributes["xmlns:android"] = "http://schemas.android.com/apk/res/android"
        if (includeXmlnsTools) attributes["xmlns:tools"] = "http://schemas.android.com/tools"
        attributes["package"] = attr_package
    }
    fun application(
        android_label: String?,
        android_name: String? = null,
        init: Application.() -> Unit
    ) = initChild(Application(android_label = android_label, android_name = android_name), init)

    fun usesSdk(
        android_minSdkVersion: String?,
        android_targetSdkVersion: String?,
        init: UsesSdk.() -> Unit
    ) =
        initChild(
            UsesSdk(
                android_minSdkVersion = android_minSdkVersion,
                android_targetSdkVersion = android_targetSdkVersion),
            init)
}

class UsesSdk(android_minSdkVersion: String?, android_targetSdkVersion: String?) : Tag("uses-sdk") {
    init {
        android_minSdkVersion?.let { attributes["android:minSdkVersion"] = android_minSdkVersion }
        android_targetSdkVersion?.let {
            attributes["android:minSdkVersion"] = android_targetSdkVersion
        }
    }
}

class Application(android_label: String?, android_name: String?) : Tag("application") {
    init {
        android_label?.let { attributes["android:label"] = it }
        android_name?.let { attributes["android:name"] = it }
    }
    fun activity(
        android_name: String?,
        android_exported: Boolean? = null,
        init: Activity.() -> Unit
    ) = initChild(Activity(android_name, android_exported), init)

    fun attr_android_backupAgent(value: String) {
        attributes["android:backupAgent"] = value
    }

    fun attr_android_localeConfig(value: String) {
        attributes["android:localeConfig"] = value
    }
}

class Activity(android_name: String?, android_exported: Boolean?) : Tag("activity") {
    init {
        android_name?.let { attributes["android:name"] = android_name }
        android_exported?.let { attributes["android:exported"] = android_exported.toString() }
    }

    fun intentFilter(init: IntentFilter.() -> Unit) = initChild(IntentFilter(), init)
}

class IntentFilter : Tag("intent-filter") {

    fun action(android_name: String, init: Action.() -> Unit) =
        initChild(Action(android_name), init)

    fun category(android_name: String, init: Category.() -> Unit) =
        initChild(Category(android_name), init)

    class Action(android_name: String) : Tag("action") {
        init {
            attributes["android:name"] = android_name
        }
    }
    class Category(android_name: String) : Tag("category") {
        init {
            attributes["android:name"] = android_name
        }
    }
}

/*
 * Type-safe builder underpinnings, see
 * https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker
 */
@DslMarker annotation class XmlTagMarker

@XmlTagMarker
open class Tag(val name: String) {
    companion object {
        const val SINGLE_WIDTH_TAB = "    "
    }
    val children = mutableListOf<Tag>()
    val attributes = linkedMapOf<String, String>()

    protected fun <T : Tag> initChild(child: T, init: T.() -> Unit): T {
        child.init()
        children += child
        return child
    }

    fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent<$name")
        val attrPrefix =
            if (attributes.size > 1) "\n$SINGLE_WIDTH_TAB$SINGLE_WIDTH_TAB$indent" else " "
        for ((attrName, attrValue) in attributes) {
            builder.append("$attrPrefix$attrName=\"$attrValue\"")
        }
        if (children.isEmpty()) {
            builder.append(" />\n")
        } else {
            builder.append(">\n")
            for (c in children) {
                c.render(builder, indent + SINGLE_WIDTH_TAB)
            }
            builder.append("$indent</$name>\n")
        }
    }

    override fun toString() = StringBuilder().also { sb -> this.render(sb, "") }.toString()
}

fun Tag.attr_tools_remove(value: String) {
    attributes["tools:remove"] = value
}

fun Tag.attr_tools_replace(value: String) {
    attributes["tools:replace"] = value
}
