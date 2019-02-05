/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_PREFIX
import com.android.SdkConstants.ANDROID_THEME_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_FOREGROUND
import com.android.SdkConstants.ATTR_FULL_BACKUP_CONTENT
import com.android.SdkConstants.ATTR_HEIGHT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LABEL_FOR
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PADDING_START
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TARGET_API
import com.android.SdkConstants.ATTR_TEXT_IS_SELECTABLE
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.ATTR_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.CHECK_BOX
import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.FQCN_FRAME_LAYOUT
import com.android.SdkConstants.FQCN_TARGET_API
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.SdkConstants.SWITCH
import com.android.SdkConstants.TAG
import com.android.SdkConstants.TAG_ANIMATED_VECTOR
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.TAG_VECTOR
import com.android.SdkConstants.TARGET_API
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_TAG
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.sdklib.SdkVersionInfo
import com.android.support.AndroidxName
import com.android.tools.lint.checks.ApiLookup.equivalentName
import com.android.tools.lint.checks.ApiLookup.startsWithEquivalentPrefix
import com.android.tools.lint.checks.RtlDetector.ATTR_SUPPORTS_RTL
import com.android.tools.lint.checks.VersionChecks.SDK_INT
import com.android.tools.lint.checks.VersionChecks.codeNameToApi
import com.android.tools.lint.checks.VersionChecks.isPrecededByVersionCheckExit
import com.android.tools.lint.checks.VersionChecks.isVersionCheckConditional
import com.android.tools.lint.checks.VersionChecks.isWithinVersionCheckConditional
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext.Companion.getFqcn
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceFolderScanner
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.UastLintUtils.getLongAttribute
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.tools.lint.detector.api.getChildren
import com.android.tools.lint.detector.api.getInternalMethodName
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.isString
import com.android.tools.lint.detector.api.skipParentheses
import com.android.utils.SdkUtils.getResourceFieldName
import com.android.utils.XmlUtils
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isChildOf
import org.jetbrains.uast.java.JavaUAnnotation
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isInstanceCheck
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.util.isTypeCast
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.lang.Boolean.TRUE
import java.util.ArrayList
import java.util.EnumSet

/**
 * Looks for usages of APIs that are not supported in all the versions targeted by this application
 * (according to its minimum API requirement in the manifest).
 */
class ApiDetector : ResourceXmlDetector(), SourceCodeScanner, ResourceFolderScanner {
    private var apiDatabase: ApiLookup? = null
    private var cachedMinApi = -1

    override fun beforeCheckRootProject(context: Context) {
        if (apiDatabase == null) {
            apiDatabase = ApiLookup.get(context.client, context.mainProject.buildTarget)
            // We can't look up the minimum API required by the project here:
            // The manifest file hasn't been processed yet in the -before- project hook.
            // For now it's initialized lazily in getMinSdk(Context), but the
            // lint infrastructure should be fixed to parse manifest file up front.
        }
    }

    // ---- Implements XmlScanner ----

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val apiDatabase = apiDatabase ?: return

        var attributeApiLevel = -1
        if (ANDROID_URI == attribute.namespaceURI) {
            val name = attribute.localName
            if (name != ATTR_LAYOUT_WIDTH &&
                name != ATTR_LAYOUT_HEIGHT &&
                name != ATTR_ID &&
                ((!isAttributeOfGradientOrGradientItem(attribute) && name != "fillType") ||
                        !dependsOnAppCompat(context.project))
            ) {
                val owner = "android/R\$attr"
                attributeApiLevel = apiDatabase.getFieldVersion(owner, name)
                val minSdk = getMinSdk(context)
                if (attributeApiLevel > minSdk &&
                    attributeApiLevel > context.folderVersion &&
                    attributeApiLevel > getLocalMinSdk(attribute.ownerElement) &&
                    !isBenignUnusedAttribute(name) &&
                    !isAlreadyWarnedDrawableFile(context, attribute, attributeApiLevel)
                ) {
                    if (RtlDetector.isRtlAttributeName(name) || ATTR_SUPPORTS_RTL == name) {
                        // No need to warn for example that
                        //  "layout_alignParentEnd will only be used in API level 17 and higher"
                        // since we have a dedicated RTL lint rule dealing with those attributes

                        // However, paddingStart in particular is known to cause crashes
                        // when used on TextViews (and subclasses of TextViews), on some
                        // devices, because vendor specific attributes conflict with the
                        // later-added framework resources, and these are apparently read
                        // by the text views.
                        //
                        // However, as of build tools 23.0.1 aapt works around this by packaging
                        // the resources differently.
                        if (name == ATTR_PADDING_START) {
                            val buildToolInfo = context.project.buildTools
                            val buildTools = buildToolInfo?.revision
                            val isOldBuildTools =
                                buildTools != null && (buildTools.major < 23 || (buildTools.major == 23 &&
                                        buildTools.minor == 0 &&
                                        buildTools.micro == 0))
                            if ((buildTools == null || isOldBuildTools) && viewMayExtendTextView(
                                    attribute.ownerElement
                                )
                            ) {
                                val location = context.getLocation(attribute)
                                val messagePart =
                                    "Attribute `${attribute.localName}` referenced here can result in a crash on " +
                                            "some specific devices older than API $attributeApiLevel " +
                                            "(current min is $minSdk)"

                                val message =
                                    if (buildTools != null) {
                                        val version = buildTools.toShortString()
                                        val lowCased = messagePart.decapitalize()
                                        "Upgrade `buildToolsVersion` from `$version` to at least `23.0.1`; if not, $lowCased"
                                    } else {
                                        messagePart
                                    }
                                context.report(
                                    UNSUPPORTED,
                                    attribute,
                                    location,
                                    message,
                                    apiLevelFix(attributeApiLevel)
                                )
                            }
                        }
                    } else {
                        val location = context.getLocation(attribute)
                        val localName = attribute.localName
                        var message =
                            "Attribute `$localName` is only used in API level $attributeApiLevel and higher (current min is $minSdk)"

                        // Supported by appcompat
                        if ("fontFamily" == localName) {
                            if (dependsOnAppCompat(context.mainProject)) {
                                val prefix = XmlUtils.lookupNamespacePrefix(
                                    attribute, AUTO_URI, "app", false
                                )
                                message += " Did you mean `$prefix:fontFamily` ?"
                            }
                        }
                        context.report(
                            UNUSED,
                            attribute,
                            location,
                            message,
                            apiLevelFix(attributeApiLevel)
                        )
                    }
                }
            }

            // Special case:
            // the dividers attribute is present in API 1, but it won't be read on older
            // versions, so don't flag the common pattern
            //    android:divider="?android:attr/dividerHorizontal"
            // since this will work just fine. See issue 36992041 for more.
            if (name == "divider") {
                return
            }

            if (name == ATTR_THEME && VIEW_INCLUDE == attribute.ownerElement.tagName) {
                // Requires API 23
                val minSdk = getMinSdk(context)
                if (Math.max(minSdk, context.folderVersion) < 23) {
                    val location = context.getLocation(attribute)
                    val message =
                        "Attribute `android:theme` is only used by `<include>` tags in API level 23 and higher (current min is $minSdk)"
                    context.report(UNUSED, attribute, location, message, apiLevelFix(23))
                }
            }

            if (name == ATTR_FOREGROUND &&
                context.resourceFolderType == ResourceFolderType.LAYOUT &&
                !isFrameLayout(context, attribute.ownerElement.tagName, true)
            ) {
                // Requires API 23, unless it's a FrameLayout
                val minSdk = getMinSdk(context)
                if (Math.max(minSdk, context.folderVersion) < 23) {
                    val location = context.getLocation(attribute)
                    val message =
                        "Attribute `android:foreground` has no effect on API levels lower than 23 (current min is $minSdk)"
                    context.report(UNUSED, attribute, location, message, apiLevelFix(23))
                }
            }
        }

        val value = attribute.value
        var owner: String? = null
        var name: String? = null
        val prefix: String?
        if (value.startsWith(ANDROID_PREFIX)) {
            prefix = ANDROID_PREFIX
        } else if (value.startsWith(ANDROID_THEME_PREFIX)) {
            prefix = ANDROID_THEME_PREFIX
            if (context.resourceFolderType == ResourceFolderType.DRAWABLE) {
                val api = 21
                val minSdk = getMinSdk(context)
                if (api > minSdk &&
                    api > context.folderVersion &&
                    api > getLocalMinSdk(attribute.ownerElement)
                ) {
                    val location = context.getLocation(attribute)
                    val message =
                        "Using theme references in XML drawables requires API level $api (current min is $minSdk)"
                    context.report(UNSUPPORTED, attribute, location, message, apiLevelFix(api))
                    // Don't flag individual theme attribute requirements here, e.g. once
                    // we've told you that you need at least v21 to reference themes, we don't
                    // need to also tell you that ?android:selectableItemBackground requires
                    // API level 11
                    return
                }
            }
        } else if (value.startsWith(PREFIX_ANDROID) &&
            ATTR_NAME == attribute.name &&
            TAG_ITEM == attribute.ownerElement.tagName &&
            attribute.ownerElement.parentNode != null &&
            TAG_STYLE == attribute.ownerElement.parentNode.nodeName
        ) {
            owner = "android/R\$attr"
            name = value.substring(PREFIX_ANDROID.length)
            prefix = null
        } else if (value.startsWith(PREFIX_ANDROID) &&
            ATTR_PARENT == attribute.name &&
            TAG_STYLE == attribute.ownerElement.tagName
        ) {
            owner = "android/R\$style"
            name = getResourceFieldName(value.substring(PREFIX_ANDROID.length))
            prefix = null
        } else {
            return
        }

        if (owner == null) {
            // Convert @android:type/foo into android/R$type and "foo"
            val index = value.indexOf('/', prefix?.length ?: 0)
            when {
                index >= 0 -> {
                    owner = "android/R$" + value.substring(prefix?.length ?: 0, index)
                    name = getResourceFieldName(value.substring(index + 1))
                }
                value.startsWith(ANDROID_THEME_PREFIX) -> {
                    owner = "android/R\$attr"
                    name = value.substring(ANDROID_THEME_PREFIX.length)
                }
                else -> return
            }
        }
        name ?: return
        val api = apiDatabase.getFieldVersion(owner, name)
        val minSdk = getMinSdk(context)
        if (api > minSdk &&
            api > context.folderVersion &&
            api > getLocalMinSdk(attribute.ownerElement)
        ) {
            // Don't complain about resource references in the tools namespace,
            // such as for example "tools:layout="@android:layout/list_content",
            // used only for designtime previews
            if (TOOLS_URI == attribute.namespaceURI) {
                return
            }

            when {
                attributeApiLevel >= api -> {
                    // The attribute will only be *read* on platforms >= attributeApiLevel.
                    // If this isn't lower than the attribute reference's API level, it
                    // won't be a problem
                }
                attributeApiLevel > minSdk -> {
                    val attributeName = attribute.localName
                    val location = context.getLocation(attribute)
                    val message =
                        "`$name` requires API level $api (current min is $minSdk), but note " +
                                "that attribute `$attributeName` is only used in API level " +
                                "$attributeApiLevel and higher"
                    context.report(UNSUPPORTED, attribute, location, message, apiLevelFix(api))
                }
                else -> {
                    if (api == 17 && RtlDetector.isRtlAttributeName(name)) {
                        val old = RtlDetector.convertNewToOld(name)
                        if (name != old) {
                            val parent = attribute.ownerElement
                            if (TAG_ITEM == parent.tagName) {
                                // Is the same style also defining the other, older attribute?
                                for (item in getChildren(parent.parentNode)) {
                                    val v = item.getAttribute(ATTR_NAME)
                                    if (v.endsWith(old)) {
                                        return
                                    }
                                }
                            } else if (parent.hasAttributeNS(ANDROID_URI, old)) {
                                return
                            }
                        }
                    }

                    val location = context.getLocation(attribute)
                    val message =
                        "`$value` requires API level $api (current min is $minSdk)"
                    context.report(UNSUPPORTED, attribute, location, message, apiLevelFix(api))
                }
            }
        }
    }

    private fun isAttributeOfGradientOrGradientItem(attribute: Attr): Boolean {
        val element = attribute.ownerElement
        if (element.nodeName == "gradient") {
            return true
        }
        if (element.nodeName == "item") {
            return element.parentNode?.localName == "gradient"
        }
        return false
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val apiDatabase = apiDatabase ?: return
        var tag: String = element.tagName

        val folderType = context.resourceFolderType
        if (folderType != ResourceFolderType.LAYOUT) {
            if (folderType == ResourceFolderType.DRAWABLE) {
                checkElement(context, element, TAG_VECTOR, 21, "1.4", UNSUPPORTED)
                checkElement(context, element, TAG_RIPPLE, 21, null, UNSUPPORTED)
                checkElement(context, element, TAG_ANIMATED_SELECTOR, 21, null, UNSUPPORTED)
                checkElement(context, element, TAG_ANIMATED_VECTOR, 21, null, UNSUPPORTED)
                checkElement(context, element, "drawable", 24, null, UNSUPPORTED)
                if ("layer-list" == tag) {
                    checkLevelList(context, element)
                } else if (tag.contains(".")) {
                    checkElement(context, element, tag, 24, null, UNSUPPORTED)
                }
            }
            if (element.parentNode.nodeType != Node.ELEMENT_NODE) {
                // Root node
                return
            }
            val childNodes = element.childNodes
            var i = 0
            val n = childNodes.length
            while (i < n) {
                val textNode = childNodes.item(i)
                if (textNode.nodeType == Node.TEXT_NODE) {
                    var text = textNode.nodeValue
                    if (text.contains(ANDROID_PREFIX)) {
                        text = text.trim()
                        // Convert @android:type/foo into android/R$type and "foo"
                        val index = text.indexOf('/', ANDROID_PREFIX.length)
                        if (index != -1) {
                            val typeString = text.substring(ANDROID_PREFIX.length, index)
                            if (ResourceType.fromXmlValue(typeString) != null) {
                                val owner = "android/R$$typeString"
                                val name = getResourceFieldName(text.substring(index + 1))
                                val api = apiDatabase.getFieldVersion(owner, name)
                                val minSdk = getMinSdk(context)
                                if (api > minSdk &&
                                    api > context.folderVersion &&
                                    api > getLocalMinSdk(element)
                                ) {
                                    val location = context.getLocation(textNode)
                                    val message =
                                        "`$text` requires API level $api (current min is $minSdk)"
                                    context.report(
                                        UNSUPPORTED,
                                        element,
                                        location,
                                        message,
                                        apiLevelFix(api)
                                    )
                                }
                            }
                        }
                    }
                }
                i++
            }
        } else {
            if (VIEW_TAG == tag) {
                tag = element.getAttribute(ATTR_CLASS) ?: return
                if (tag.isEmpty()) {
                    return
                }
            } else {
                // TODO: Complain if <tag> is used at the root level!
                checkElement(context, element, TAG, 21, null, UNUSED)
            }

            // Check widgets to make sure they're available in this version of the SDK.
            if (tag.indexOf('.') != -1) {
                // Custom views aren't in the index
                return
            }
            var fqn = "android/widget/$tag"
            if (tag == "TextureView") {
                fqn = "android/view/TextureView"
            }
            // TODO: Consider other widgets outside of android.widget.*
            val api = apiDatabase.getClassVersion(fqn)
            val minSdk = getMinSdk(context)
            if (api > minSdk && api > context.folderVersion && api > getLocalMinSdk(element)) {
                val location = context.getNameLocation(element)
                val message =
                    "View requires API level $api (current min is $minSdk): `<$tag>`"
                context.report(UNSUPPORTED, element, location, message, apiLevelFix(api))
            }
        }
    }

    /**
     * Checks whether the given element is the given tag, and if so, whether it satisfied the
     * minimum version that the given tag is supported in
     */
    private fun checkLevelList(context: XmlContext, element: Element) {
        var curr: Node? = element.firstChild
        while (curr != null) {
            if (curr.nodeType == Node.ELEMENT_NODE && TAG_ITEM == curr.nodeName) {
                val e = curr as Element
                if (e.hasAttributeNS(ANDROID_URI, ATTR_WIDTH) || e.hasAttributeNS(
                        ANDROID_URI,
                        ATTR_HEIGHT
                    )
                ) {
                    val attributeApiLevel =
                        23 // Using width and height on layer-list children requires M
                    val minSdk = getMinSdk(context)
                    if (attributeApiLevel > minSdk &&
                        attributeApiLevel > context.folderVersion &&
                        attributeApiLevel > getLocalMinSdk(element)
                    ) {
                        for (attributeName in arrayOf(ATTR_WIDTH, ATTR_HEIGHT)) {
                            val attribute =
                                e.getAttributeNodeNS(ANDROID_URI, attributeName) ?: continue
                            val location = context.getLocation(attribute)
                            val message =
                                "Attribute `${attribute.localName}` is only used in API level $attributeApiLevel and higher (current min is $minSdk)"
                            context.report(
                                UNUSED,
                                attribute,
                                location,
                                message,
                                apiLevelFix(attributeApiLevel)
                            )
                        }
                    }
                }
            }
            curr = curr.nextSibling
        }
    }

    /**
     * Checks whether the given element is the given tag, and if so, whether it satisfied the
     * minimum version that the given tag is supported in
     */
    private fun checkElement(
        context: XmlContext,
        element: Element,
        tag: String,
        api: Int,
        gradleVersion: String?,
        issue: Issue
    ) {
        var realTag = tag
        if (realTag == element.tagName) {
            val minSdk = getMinSdk(context)
            if (api > minSdk &&
                api > context.folderVersion &&
                api > getLocalMinSdk(element) &&
                !featureProvidedByGradle(context, gradleVersion)
            ) {
                var location = context.getNameLocation(element)

                // For the <drawable> tag we report it against the class= attribute
                if ("drawable" == realTag) {
                    val attribute = element.getAttributeNode(ATTR_CLASS) ?: return
                    location = context.getLocation(attribute)
                    realTag = ATTR_CLASS
                }

                var message: String
                if (issue === UNSUPPORTED) {
                    message =
                            "`<$realTag>` requires API level $api (current min is $minSdk)"
                    if (gradleVersion != null) {
                        message +=
                                " or building with Android Gradle plugin $gradleVersion or higher"
                    } else if (realTag.contains(".")) {
                        message =
                                "Custom drawables requires API level $api (current min is $minSdk)"
                    }
                } else {
                    assert(issue === UNUSED) { issue }
                    message =
                            "`<$realTag>` is only used in API level $api and higher (current min is $minSdk)"
                }
                context.report(issue, element, location, message, apiLevelFix(api))
            }
        }
    }

    private fun getMinSdk(context: Context): Int {
        if (cachedMinApi == -1) {
            val minSdkVersion = context.mainProject.minSdkVersion
            cachedMinApi = minSdkVersion.featureLevel
            if (cachedMinApi == 1 && !context.mainProject.isAndroidProject) {
                // Don't flag API checks in non-Android projects
                cachedMinApi = Integer.MAX_VALUE
            }
        }

        return cachedMinApi
    }

    // ---- implements SourceCodeScanner ----

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        if (apiDatabase == null || context.isTestSource) {
            return null
        }
        return if (!context.mainProject.isAndroidProject) {
            null
        } else ApiVisitor(context)
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(
            USimpleNameReferenceExpression::class.java,
            ULocalVariable::class.java,
            UTryExpression::class.java,
            UBinaryExpressionWithType::class.java,
            UBinaryExpression::class.java,
            UCallExpression::class.java,
            UClass::class.java,
            UMethod::class.java,
            UForEachExpression::class.java,
            UClassLiteralExpression::class.java,
            USwitchExpression::class.java,
            UCallableReferenceExpression::class.java
        )
    }

    private inner class ApiVisitor(private val context: JavaContext) : UElementHandler() {
        private fun report(
            issue: Issue,
            node: UElement,
            location: Location,
            message: String,
            fix: LintFix? = null,
            owner: String? = null,
            name: String? = null,
            @Suppress("UNUSED_PARAMETER")
            desc: String? = null
        ) {
            // Java 8 API desugaring?
            if (owner != null && (owner.startsWith("java/") || owner.startsWith("java.")) &&
                context.mainProject.isDesugaring(Desugaring.JAVA_8_LIBRARY) &&
                isApiDesugared(context, owner.replace('/', '.'), name)
            ) {
                return
            }

            context.report(issue, node, location, message, fix)
        }

        override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
        ) {
            val resolved = node.resolve()
            if (resolved is PsiField) {
                checkField(node, resolved)
            } else if (resolved is PsiMethod && node is UCallExpression) {
                checkMethodReference(node, resolved)
            }
        }

        override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
            val resolved = node.resolve()
            if (resolved is PsiMethod) {
                checkMethodReference(node, resolved)
            }
        }

        private fun checkMethodReference(expression: UReferenceExpression, method: PsiMethod) {
            val apiDatabase = apiDatabase ?: return

            val containingClass = method.containingClass ?: return
            val evaluator = context.evaluator
            val owner = evaluator.getQualifiedName(containingClass)
                ?: return // Couldn't resolve type
            if (!apiDatabase.containsClass(owner)) {
                return
            }

            val name = getInternalMethodName(method)
            val desc = evaluator.getMethodDescription(
                method,
                false,
                false
            ) // Couldn't compute description of method for some reason; probably
            // failure to resolve parameter types
                ?: return

            val api = apiDatabase.getMethodVersion(owner, name, desc)
            if (api == -1) {
                return
            }
            val minSdk = getMinSdk(context)
            if (isSuppressed(context, api, expression, minSdk)) {
                return
            }

            val signature = expression.asSourceString()
            val location = context.getLocation(expression)
            val min = Math.max(minSdk, getTargetApi(expression))
            val message =
                "Method reference requires API level $api (current min is $min): `$signature`"
            report(UNSUPPORTED, expression, location, message, apiLevelFix(api), owner, name, desc)
        }

        override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
            if (node.isTypeCast()) {
                visitTypeCastExpression(node)
            } else if (node.isInstanceCheck()) {
                val typeReference = node.typeReference
                if (typeReference != null) {
                    val type = typeReference.type
                    if (type is PsiClassType) {
                        checkClassReference(typeReference, type)
                    }
                }
            }
        }

        private fun visitTypeCastExpression(expression: UBinaryExpressionWithType) {
            val operand = expression.operand
            val operandType = operand.getExpressionType()
            val castType = expression.type
            if (castType == operandType) {
                return
            }
            if (operandType !is PsiClassType) {
                return
            }
            if (castType !is PsiClassType) {
                return
            }

            val typeReference = expression.typeReference
            if (typeReference != null) {
                if (!checkClassReference(typeReference, castType)) {
                    // Found problem with cast type itself: don't bother also warning
                    // about problem with LHS
                    return
                }
            }

            checkCast(expression, operandType, castType)
        }

        private fun checkClassReference(
            node: UElement,
            classType: PsiClassType
        ): Boolean {
            val apiDatabase = apiDatabase ?: return true
            val evaluator = context.evaluator
            val expressionOwner = evaluator.getQualifiedName(classType) ?: return true
            val api = apiDatabase.getClassVersion(expressionOwner)
            if (api == -1) {
                return true
            }
            val minSdk = getMinSdk(context)
            if (isSuppressed(context, api, node, minSdk)) {
                return true
            }

            val min = Math.max(minSdk, getTargetApi(node))
            val message =
                "Class requires API level $api (current min is $min): `$expressionOwner`"

            val location = context.getLocation(node)
            report(UNSUPPORTED, node, location, message, apiLevelFix(api), expressionOwner)
            return false
        }

        private fun checkCast(
            node: UElement,
            classType: PsiClassType,
            interfaceType: PsiClassType
        ) {
            val apiDatabase = apiDatabase ?: return
            if (classType == interfaceType) {
                return
            }
            val evaluator = context.evaluator
            val classTypeInternal = evaluator.getQualifiedName(classType)
            val interfaceTypeInternal = evaluator.getQualifiedName(interfaceType)
            if (interfaceTypeInternal == null || classTypeInternal == null) {
                return
            }
            if (equivalentName(interfaceTypeInternal, "java/lang/Object")) {
                return
            }

            val api = apiDatabase.getValidCastVersion(classTypeInternal, interfaceTypeInternal)
            if (api == -1) {
                return
            }

            val minSdk = getMinSdk(context)
            if (api <= minSdk) {
                return
            }

            if (isSuppressed(context, api, node, minSdk)) {
                return
            }

            val location = context.getLocation(node)
            val message: String
            val to = interfaceType.className
            val from = classType.className
            val min = Math.max(minSdk, getTargetApi(node))
            message = if (interfaceTypeInternal == classTypeInternal) {
                "Cast to `$to` requires API level $api (current min is $min)"
            } else {
                "Cast from `$from` to `$to` requires API level $api (current min is $min)"
            }

            report(UNSUPPORTED, node, location, message, apiLevelFix(api), classTypeInternal)
        }

        override fun visitMethod(node: UMethod) {
            val apiDatabase = apiDatabase ?: return
            val containingClass = node.containingClass

            // API check for default methods
            if (containingClass != null &&
                containingClass.isInterface &&
                // (unless using desugar which supports this for all API levels)
                !context.mainProject.isDesugaring(Desugaring.INTERFACE_METHODS)
            ) {
                val methodModifierList = node.modifierList
                if (methodModifierList.hasExplicitModifier(PsiModifier.DEFAULT) || methodModifierList.hasExplicitModifier(
                        PsiModifier.STATIC
                    )
                ) {
                    val api = 24 // minSdk for default methods
                    val minSdk = getMinSdk(context)

                    if (!isSuppressed(context, api, node, minSdk)) {
                        val location = context.getLocation(node)
                        val desc = if (methodModifierList.hasExplicitModifier(PsiModifier.DEFAULT))
                            "Default"
                        else
                            "Static interface"
                        val min = Math.max(minSdk, getTargetApi(node))
                        val message =
                            "$desc method requires API level $api (current min is $min)"
                        report(
                            UNSUPPORTED,
                            node,
                            location,
                            message,
                            apiLevelFix(api),
                            containingClass.qualifiedName
                        )
                    }
                }
            }

            val buildSdk = context.mainProject.buildSdk
            val name = node.name
            val evaluator = context.evaluator
            var superMethod = evaluator.getSuperMethod(node)
            while (superMethod != null) {
                val cls = superMethod.containingClass ?: break
                var fqcn = cls.qualifiedName ?: break
                if (fqcn.startsWith("android.") ||
                    fqcn.startsWith("java.") && fqcn != CommonClassNames.JAVA_LANG_OBJECT ||
                    fqcn.startsWith("javax.")
                ) {
                    val desc = evaluator.getMethodDescription(superMethod, false, false)
                    if (desc != null) {
                        val owner = evaluator.getQualifiedName(cls) ?: return
                        val api = apiDatabase.getMethodVersion(owner, name, desc)
                        if (api > buildSdk && buildSdk != -1) {
                            if (context.driver
                                    .isSuppressed(context, OVERRIDE, node as UElement)
                            ) {
                                return
                            }

                            // TODO: Don't complain if it's annotated with @Override; that means
                            // somehow the build target isn't correct.
                            if (containingClass != null) {
                                var className = containingClass.name
                                val fullClassName = containingClass.qualifiedName
                                if (fullClassName != null) {
                                    className = fullClassName
                                }
                                fqcn = className + '#'.toString() + name
                            } else {
                                fqcn = name
                            }

                            val message =
                                "This method is not overriding anything with the current " +
                                        "build target, but will in API level $api (current " +
                                        "target is $buildSdk): `$fqcn`"
                            var locationNode: PsiElement? = node.nameIdentifier
                            if (locationNode == null) {
                                locationNode = node
                            }
                            val location = context.getLocation(locationNode)
                            report(OVERRIDE, node, location, message)
                        }
                    }
                } else {
                    break
                }

                superMethod = evaluator.getSuperMethod(superMethod)
            }
        }

        override fun visitClass(node: UClass) {
            // Check for repeatable and type annotations
            if (node.isAnnotationType &&
                // Desugar adds support for type annotations
                !context.mainProject.isDesugaring(Desugaring.TYPE_ANNOTATIONS)
            ) {
                val modifierList = node.modifierList
                if (modifierList != null) {
                    for (annotation in modifierList.annotations) {
                        val name = annotation.qualifiedName
                        if ("java.lang.annotation.Repeatable" == name) {
                            val api = 24 // minSdk for repeatable annotations
                            val minSdk = getMinSdk(context)
                            if (!isSuppressed(context, api, node, minSdk)) {
                                val location = context.getLocation(annotation)
                                val min = Math.max(minSdk, getTargetApi(node))
                                val message =
                                    "Repeatable annotation requires API level $api (current min is $min)"
                                context.report(
                                    UNSUPPORTED,
                                    annotation,
                                    location,
                                    message,
                                    apiLevelFix(api)
                                )
                            }
                        } else if ("java.lang.annotation.Target" == name) {
                            val attributes = annotation.parameterList.attributes
                            for (pair in attributes) {
                                val value = pair.value
                                if (value is PsiArrayInitializerMemberValue) {
                                    for (t in value.initializers) {
                                        checkAnnotationTarget(t, modifierList)
                                    }
                                } else if (value != null) {
                                    checkAnnotationTarget(value, modifierList)
                                }
                            }
                        }
                    }
                }
            }

            // Check super types
            for (typeReferenceExpression in node.uastSuperTypes) {
                val type = typeReferenceExpression.type
                if (type is PsiClassType) {
                    val cls = type.resolve()
                    if (cls != null) {
                        checkClass(typeReferenceExpression, cls)
                    }
                }
            }
        }

        override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
            val type = node.type
            if (type is PsiClassType) {
                val lhs = node.expression
                val locationElement = lhs ?: node
                checkClassType(locationElement, type, null)
            }
        }

        private fun checkClassType(
            element: UElement,
            classType: PsiClassType,
            descriptor: String?
        ) {
            val owner = context.evaluator.getQualifiedName(classType)
            val fqcn = classType.canonicalText
            if (owner != null) {
                checkClass(element, descriptor, owner, fqcn)
            }
        }

        private fun checkClass(element: UElement, cls: PsiClass) {
            val owner = context.evaluator.getQualifiedName(cls) ?: return
            val fqcn = cls.qualifiedName
            if (fqcn != null) {
                checkClass(element, null, owner, fqcn)
            }
        }

        private fun checkClass(
            element: UElement,
            descriptor: String?,
            owner: String,
            fqcn: String
        ) {
            val apiDatabase = apiDatabase ?: return
            val api = apiDatabase.getClassVersion(owner)
            if (api == -1) {
                return
            }
            val minSdk = getMinSdk(context)
            if (isSuppressed(context, api, element, minSdk)) {
                return
            }

            // It's okay to reference classes from annotations
            if (element.getParentOfType<UElement>(UAnnotation::class.java) != null) {
                return
            }

            val location = context.getNameLocation(element)
            val min = Math.max(minSdk, getTargetApi(element))
            val message =
                "${descriptor ?: "Class"} requires API level $api (current min is $min): `$fqcn`"
            report(UNSUPPORTED, element, location, message, apiLevelFix(api), owner)
        }

        private fun checkAnnotationTarget(
            element: PsiAnnotationMemberValue,
            modifierList: PsiModifierList
        ) {
            if (element is UReferenceExpression) {
                val referenceName = UastLintUtils.getReferenceName(element)
                if ("TYPE_PARAMETER" == referenceName || "TYPE_USE" == referenceName) {
                    val retention = modifierList.findAnnotation("java.lang.annotation.Retention")
                    if (retention == null || retention.text.contains("RUNTIME")) {
                        val location = context.getLocation(element as UElement)
                        val message =
                            "Type annotations are not supported in Android: `$referenceName`"
                        report(UNSUPPORTED, element as UElement, location, message)
                    }
                }
            }
        }

        override fun visitForEachExpression(node: UForEachExpression) {
            // The for each method will implicitly call iterator() on the
            // Iterable that is used in the for each loop; make sure that
            // the API level for that

            val apiDatabase = apiDatabase ?: return
            val value = node.iteratedValue

            val evaluator = context.evaluator
            val type = value.getExpressionType()
            if (type is PsiClassType) {
                val expressionOwner = evaluator.getQualifiedName(type) ?: return
                val api = apiDatabase.getClassVersion(expressionOwner)
                if (api == -1) {
                    return
                }
                val minSdk = getMinSdk(context)
                if (isSuppressed(context, api, node, minSdk)) {
                    return
                }

                val location = context.getLocation(value)
                val min = Math.max(minSdk, getTargetApi(node))
                var message =
                    "The type of the for loop iterated value is " +
                            "${type.canonicalText}, which requires API level $api" +
                            " (current min is $min)"

                // Add specific check ConcurrentHashMap#keySet and add workaround text.
                // This was an unfortunate incompatible API change in Open JDK 8, which is
                // not an issue for the Android SDK but is relevant if you're using a
                // Java library.
                if (value is UQualifiedReferenceExpression) {
                    if ("keySet" == value.resolvedName) {
                        val keySet = value.resolve()
                        if (keySet is PsiMethod) {
                            val containingClass = keySet.containingClass
                            if (containingClass != null &&
                                "java.util.concurrent.ConcurrentHashMap" == containingClass.qualifiedName
                            ) {
                                message += "; to work around this, add an explicit cast to `(Map)` before the `keySet` call."
                            }
                        }
                    }
                }
                report(UNSUPPORTED, node, location, message, apiLevelFix(api), expressionOwner)
            }
        }

        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve()
            if (method == null) {
                // If it's a constructor call to a default constructor, resolve() returns
                // null. But we still want to check @RequiresApi for these; we won't
                // run into this for the APIs recorded in the database since those
                // are always referenced from .class files where we have the actual
                // constructor.
                val reference = node.classReference
                if (reference != null) {
                    val resolved = reference.resolve()
                    if (resolved is PsiClass) {
                        checkRequiresApi(node, resolved, resolved.modifierList)
                    }
                }

                return
            }

            visitCall(method, node, node)
        }

        private fun visitCall(
            method: PsiMethod,
            call: UCallExpression?,
            reference: UElement
        ) {
            val apiDatabase = apiDatabase ?: return
            val containingClass = method.containingClass ?: return

            // Enforce @RequiresApi
            if (!checkRequiresApi(reference, method, method.modifierList)) {
                checkRequiresApi(reference, method, containingClass.modifierList)
            }

            val parameterList = method.parameterList
            if (parameterList.parametersCount > 0 && call != null) {
                val parameters = parameterList.parameters
                val arguments = call.valueArguments
                for (i in parameters.indices) {
                    val parameterType = parameters[i].type
                    if (parameterType is PsiClassType) {
                        if (i >= arguments.size) {
                            // We can end up with more arguments than parameters when
                            // there is a varargs call.
                            break
                        }
                        val argument = arguments[i]
                        val argumentType = argument.getExpressionType()
                        if (argumentType == null ||
                            parameterType == argumentType ||
                            argumentType !is PsiClassType
                        ) {
                            continue
                        }
                        checkCast(
                            argument,
                            argumentType,
                            parameterType
                        )
                    }
                }
            }

            val evaluator = context.evaluator
            val owner = evaluator.getQualifiedName(containingClass)
                ?: return // Couldn't resolve type

            // Support library: we can do compile time resolution
            if (startsWithEquivalentPrefix(owner, "android/support/")) {
                return
            }
            if (!apiDatabase.containsClass(owner)) {
                return
            }

            val name = getInternalMethodName(method)
            val desc = evaluator.getMethodDescription(
                method,
                false,
                false
            ) // Couldn't compute description of method for some reason; probably
            // failure to resolve parameter types
                ?: return

            if (call != null &&
                startsWithEquivalentPrefix(owner, "java/text/SimpleDateFormat") &&
                name == CONSTRUCTOR_NAME &&
                desc != "()V"
            ) {
                checkSimpleDateFormat(context, call, getMinSdk(context))
            }

            var api = apiDatabase.getMethodVersion(owner, name, desc)
            if (api == -1) {
                return
            }
            val minSdk = getMinSdk(context)
            if (api <= minSdk) {
                return
            }

            var fqcn = containingClass.qualifiedName

            // The lint API database contains two optimizations:
            // First, all members that were available in API 1 are omitted from the database,
            // since that saves about half of the size of the database, and for API check
            // purposes, we don't need to distinguish between "doesn't exist" and "available
            // in all versions".

            // Second, all inherited members were inlined into each class, so that it doesn't
            // have to do a repeated search up the inheritance chain.
            //
            // Unfortunately, in this custom PSI detector, we look up the real resolved method,
            // which can sometimes have a different minimum API.
            //
            // For example, SQLiteDatabase had a close() method from API 1. Therefore, calling
            // SQLiteDatabase is supported in all versions. However, it extends SQLiteClosable,
            // which in API 16 added "implements Closable". In this detector, if we have the
            // following code:
            //     void test(SQLiteDatabase db) { db.close }
            // here the call expression will be the close method on type SQLiteClosable. And
            // that will result in an API requirement of API 16, since the close method it now
            // resolves to is in API 16.
            //
            // To work around this, we can now look up the type of the call expression ("db"
            // in the above, but it could have been more complicated), and if that's a
            // different type than the type of the method, we look up *that* method from
            // lint's database instead. Furthermore, it's possible for that method to return
            // "-1" and we can't tell if that means "doesn't exist" or "present in API 1", we
            // then check the package prefix to see whether we know it's an API method whose
            // members should all have been inlined.
            if (call != null && call.isMethodCall()) {
                val qualifier = call.receiver
                if (qualifier != null &&
                    qualifier !is UThisExpression &&
                    qualifier !is PsiSuperExpression
                ) {
                    val receiverType = qualifier.getExpressionType()
                    if (receiverType is PsiClassType) {
                        val containingType = context.evaluator.getClassType(containingClass)
                        val inheritanceChain =
                            getInheritanceChain(receiverType, containingType)
                        if (inheritanceChain != null) {
                            for (type in inheritanceChain) {
                                val expressionOwner = evaluator.getQualifiedName(type)
                                if (expressionOwner != null && expressionOwner != owner) {
                                    val specificApi = apiDatabase.getMethodVersion(
                                        expressionOwner, name, desc
                                    )
                                    if (specificApi == -1) {
                                        if (apiDatabase.isRelevantOwner(expressionOwner)) {
                                            return
                                        }
                                    } else if (specificApi <= minSdk) {
                                        return
                                    } else {
                                        // For example, for Bundle#getString(String,String) the API level
                                        // is 12, whereas for BaseBundle#getString(String,String) the API
                                        // level is 21. If the code specified a Bundle instead of
                                        // a BaseBundle, reported the Bundle level in the error message
                                        // instead.
                                        if (specificApi < api) {
                                            api = specificApi
                                            fqcn = type.canonicalText
                                        }
                                        api = Math.min(specificApi, api)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Unqualified call; need to search in our super hierarchy
                    // Unfortunately, expression.getReceiverType() does not work correctly
                    // in Java; it returns the type of the static binding of the call
                    // instead of giving the virtual dispatch type, as described in
                    // https://issuetracker.google.com/64528052 (and covered by
                    // for example ApiDetectorTest#testListView). Therefore, we continue
                    // to use the workaround method for Java (which isn't correct, and is
                    // particularly broken in Kotlin where the dispatch needs to take into
                    // account top level functions and extension methods), and then we use
                    // the correct receiver type in Kotlin.
                    var cls: PsiClass? = null
                    if (context.file.path.endsWith(DOT_JAVA)) {
                        cls = call.getContainingUClass()?.javaPsi
                    } else {
                        val receiverType = call.receiverType
                        if (receiverType is PsiClassType) {
                            cls = receiverType.resolve()
                        }
                    }

                    if (qualifier is UThisExpression || qualifier is USuperExpression) {
                        val pte = qualifier as UInstanceExpression
                        val resolved = pte.resolve()
                        if (resolved is PsiClass) {
                            cls = resolved
                        }
                    }

                    while (cls != null) {
                        if (cls is PsiAnonymousClass) {
                            // If it's an unqualified call in an anonymous class, we need to
                            // rely on the resolve method to find out whether the method is
                            // picked up from the anonymous class chain or any outer classes
                            var found = false
                            val anonymousBaseType = cls.baseClassType
                            val anonymousBase = anonymousBaseType.resolve()
                            if (anonymousBase != null && anonymousBase.isInheritor(
                                    containingClass,
                                    true
                                )
                            ) {
                                cls = anonymousBase
                                found = true
                            } else {
                                val surroundingBaseType =
                                    PsiTreeUtil.getParentOfType(cls, PsiClass::class.java, true)
                                if (surroundingBaseType != null && surroundingBaseType.isInheritor(
                                        containingClass,
                                        true
                                    )
                                ) {
                                    cls = surroundingBaseType
                                    found = true
                                }
                            }
                            if (!found) {
                                break
                            }
                        }
                        val expressionOwner = evaluator.getQualifiedName(cls)
                        if (expressionOwner == null || equivalentName(
                                expressionOwner,
                                "java/lang/Object"
                            )
                        ) {
                            break
                        }
                        val specificApi =
                            apiDatabase.getMethodVersion(expressionOwner, name, desc)
                        if (specificApi == -1) {
                            if (apiDatabase.isRelevantOwner(expressionOwner)) {
                                break
                            }
                        } else if (specificApi <= minSdk) {
                            return
                        } else {
                            if (specificApi < api) {
                                api = specificApi
                                fqcn = cls.qualifiedName
                            }
                            api = Math.min(specificApi, api)
                            break
                        }
                        cls = cls.superClass
                    }
                }
            }

            if (isSuppressed(context, api, reference, minSdk)) {
                return
            }

            if (call != null && call.isMethodCall()) {
                val receiver = call.receiver

                var target: PsiClass? = null
                if (!method.isConstructor) {
                    if (receiver != null) {
                        val type = receiver.getExpressionType()
                        if (type is PsiClassType) {
                            target = type.resolve()
                        }
                    } else {
                        target = call.getContainingUClass()?.javaPsi
                    }
                }

                // Look to see if there's a possible local receiver
                if (target != null) {
                    val methods = target.findMethodsBySignature(method, true)
                    if (methods.size > 1) {
                        for (m in methods) {
                            if (method != m) {
                                val provider = m.containingClass
                                if (provider != null) {
                                    val methodOwner = evaluator.getQualifiedName(provider)
                                    if (methodOwner != null) {
                                        val methodApi = apiDatabase.getMethodVersion(
                                            methodOwner, name, desc
                                        )
                                        if (methodApi == -1 || methodApi <= minSdk) {
                                            // Yes, we found another call that doesn't have an API requirement
                                            return
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // If you're simply calling super.X from method X, even if method X is in a higher
                // API level than the minSdk, we're generally safe; that method should only be
                // called by the framework on the right API levels. (There is a danger of somebody
                // calling that method locally in other contexts, but this is hopefully unlikely.)
                if (receiver is USuperExpression) {
                    val containingMethod = call.getContainingUMethod()?.javaPsi
                    if (containingMethod != null &&
                        name == containingMethod.name &&
                        evaluator.areSignaturesEqual(method, containingMethod) &&
                        // We specifically exclude constructors from this check, because we
                        // do want to flag constructors requiring the new API level; it's
                        // highly likely that the constructor is called by local code so
                        // you should specifically investigate this as a developer
                        !method.isConstructor
                    ) {
                        return
                    }
                }

                // If it's a method we have source for, obviously it shouldn't be a
                // violation. (This happens for example when compiling the support library.)
                if (method !is PsiCompiledElement) {
                    return
                }
            }

            // Desugar rewrites compare calls (see b/36390874)
            if (name == "compare" &&
                api == 19 &&
                startsWithEquivalentPrefix(owner, "java/lang/") &&
                desc.length == 4 &&
                context.mainProject.isDesugaring(Desugaring.LONG_COMPARE) &&
                (desc == "(JJ)" ||
                        desc == "(ZZ)" ||
                        desc == "(BB)" ||
                        desc == "(CC)" ||
                        desc == "(II)" ||
                        desc == "(SS)")
            ) {
                return
            }

            // Desugar rewrites Objects.requireNonNull calls (see b/32446315)
            if (name == "requireNonNull" &&
                api == 19 &&
                owner == "java.util.Objects" &&
                desc == "(Ljava.lang.Object;)" &&
                context.mainProject.isDesugaring(Desugaring.OBJECTS_REQUIRE_NON_NULL)
            ) {
                return
            }

            if (name == "addSuppressed" &&
                api == 19 &&
                owner == "java.lang.Throwable" &&
                desc == "(Ljava.lang.Throwable;)" &&
                context.mainProject.isDesugaring(Desugaring.TRY_WITH_RESOURCES)
            ) {
                return
            }

            val signature: String
            signature =
                    if (fqcn == null) {
                        name
                    } else if (CONSTRUCTOR_NAME == name) {
                        if (isKotlin(reference.sourcePsi)) {
                            "$fqcn()"
                        } else {
                            "new $fqcn"
                        }
                    } else {
                        "$fqcn${'#'}$name"
                    }

            val nameIdentifier = if (call != null) call.methodIdentifier else reference

            val location = if (call != null &&
                call.isConstructorCall() &&
                call.classReference != null
            ) {
                context.getRangeLocation(call, 0, call.classReference!!, 0)
            } else if (nameIdentifier != null) {
                context.getLocation(nameIdentifier)
            } else {
                context.getLocation(reference)
            }
            val min = Math.max(minSdk, getTargetApi(reference))
            val message =
                "Call requires API level $api (current min is $min): `$signature`"

            report(UNSUPPORTED, reference, location, message, apiLevelFix(api), owner, name, desc)
        }

        private fun getRequiresApiFromAnnotations(modifierList: PsiModifierList): Int {
            for (annotation in modifierList.annotations) {
                val qualifiedName = annotation.qualifiedName
                if (REQUIRES_API_ANNOTATION.isEquals(qualifiedName)) {
                    val wrapped = JavaUAnnotation.wrap(annotation)
                    var api = getLongAttribute(context, wrapped, ATTR_VALUE, -1).toInt()
                    if (api <= 1) {
                        // @RequiresApi has two aliasing attributes: api and value
                        api = getLongAttribute(context, wrapped, "api", -1).toInt()
                    }
                    return api
                } else if (qualifiedName == null) {
                    // Work around UAST type resolution problems
                    // Work around bugs in UAST type resolution for file annotations:
                    // parse the source string instead.
                    if (annotation is PsiCompiledElement) {
                        continue
                    }
                    val text = annotation.text
                    if (text.contains("RequiresApi(")) {
                        val start = text.indexOf('(')
                        val end = text.indexOf(')', start + 1)
                        if (end != -1) {
                            var name = text.substring(start + 1, end)
                            // Strip off attribute name and qualifiers, e.g.
                            //   @RequiresApi(api = Build.VERSION.O) -> O
                            var index = name.indexOf('=')
                            if (index != -1) {
                                name = name.substring(index + 1).trim()
                            }
                            index = name.indexOf('.')
                            if (index != -1) {
                                name = name.substring(index + 1)
                            }
                            if (!name.isEmpty()) {
                                if (name[0].isDigit()) {
                                    val api = Integer.parseInt(name)
                                    if (api > 0) {
                                        return api
                                    }
                                } else {
                                    return codeNameToApi(name)
                                }
                            }
                        }
                    }
                }
            }

            return -1
        }

        // Look for @RequiresApi in modifier lists
        private fun checkRequiresApi(
            expression: UElement,
            member: PsiMember,
            modifierList: PsiModifierList?
        ): Boolean {
            modifierList ?: return false

            val api = getRequiresApiFromAnnotations(modifierList)
            if (api != -1) {
                val minSdk = getMinSdk(context)
                if (api > minSdk) {
                    val target = getTargetApi(expression)
                    if (target == -1 || api > target) {
                        if (isWithinVersionCheckConditional(context.evaluator, expression, api)) {
                            return true
                        }
                        if (isPrecededByVersionCheckExit(expression, api)) {
                            return true
                        }

                        val location: Location
                        val fqcn: String?
                        if (expression is UCallExpression &&
                            expression.kind != UastCallKind.METHOD_CALL &&
                            expression.classReference != null
                        ) {
                            val classReference = expression.classReference!!
                            location = context.getRangeLocation(expression, 0, classReference, 0)
                            fqcn = classReference.resolvedName ?: member.name ?: ""
                        } else {
                            location = context.getNameLocation(expression)
                            fqcn = member.name ?: ""
                        }

                        val min = Math.max(minSdk, getTargetApi(expression))
                        val message =
                            "Call requires API level $api (current min is $min): `$fqcn`"
                        report(UNSUPPORTED, expression, location, message, apiLevelFix(api))
                    }
                }

                return true
            }

            return false
        }

        override fun visitLocalVariable(node: ULocalVariable) {
            val initializer = node.uastInitializer ?: return

            val initializerType = initializer.getExpressionType() as? PsiClassType ?: return

            val interfaceType = node.type
            if (initializerType == interfaceType) {
                return
            }

            if (interfaceType !is PsiClassType) {
                return
            }

            checkCast(initializer, initializerType, interfaceType)
        }

        override fun visitBinaryExpression(node: UBinaryExpression) {
            val operator = node.operator
            if (operator is UastBinaryOperator.AssignOperator) {
                val rExpression = node.rightOperand
                val rhsType = rExpression.getExpressionType() as? PsiClassType ?: return

                val interfaceType = node.leftOperand.getExpressionType()
                if (rhsType == interfaceType) {
                    return
                }

                if (interfaceType !is PsiClassType) {
                    return
                }

                checkCast(rExpression, rhsType, interfaceType)
            } else if (operator === UastBinaryOperator.OTHER) {
                val method = node.resolveOperator()
                if (method != null) {
                    visitCall(method, null, node)
                }
            }
        }

        override fun visitTryExpression(node: UTryExpression) {
            val resourceList = node.resourceVariables

            if (!resourceList.isEmpty() &&
                // (unless using desugar which supports this for all API levels)
                !context.mainProject.isDesugaring(Desugaring.TRY_WITH_RESOURCES)
            ) {

                val api = 19 // minSdk for try with resources
                val minSdk = getMinSdk(context)

                if (api > minSdk && api > getTargetApi(node)) {
                    if (isSuppressed(context, api, node, minSdk)) {
                        return
                    }

                    // Create location range for the resource list
                    val first = resourceList[0]
                    val last = resourceList[resourceList.size - 1]
                    val location = context.getRangeLocation(first, 0, last, 0)

                    val min = Math.max(minSdk, getTargetApi(node))
                    val message =
                        "Try-with-resources requires API level $api (current min is $min)"
                    report(UNSUPPORTED, node, location, message, apiLevelFix(api))
                }
            }

            for (catchClause in node.catchClauses) {
                // Special case reflective operation exception which can be implicitly used
                // with multi-catches: see issue 153406
                val minSdk = getMinSdk(context)
                if (minSdk < 19 && isMultiCatchReflectiveOperationException(catchClause)) {
                    if (isSuppressed(context, 19, node, minSdk)) {
                        return
                    }

                    val message =
                        "Multi-catch with these reflection exceptions requires API level 19 (current min is $minSdk) " +
                                "because they get compiled to the common but new super type `ReflectiveOperationException`. " +
                                "As a workaround either create individual catch statements, or catch `Exception`."

                    report(
                        UNSUPPORTED,
                        node,
                        getCatchParametersLocation(context, catchClause),
                        message,
                        apiLevelFix(19)
                    )
                    continue
                }

                for (typeReference in catchClause.typeReferences) {
                    checkCatchTypeElement(node, typeReference, typeReference.type)
                }
            }
        }

        private fun checkCatchTypeElement(
            statement: UTryExpression,
            typeReference: UTypeReferenceExpression,
            type: PsiType?
        ) {
            val apiDatabase = apiDatabase ?: return
            var resolved: PsiClass? = null
            if (type is PsiClassType) {
                resolved = type.resolve()
            }
            if (resolved != null) {
                val signature = context.evaluator.getQualifiedName(resolved) ?: return
                val api = apiDatabase.getClassVersion(signature)
                if (api == -1) {
                    return
                }
                val minSdk = getMinSdk(context)
                if (api <= minSdk) {
                    return
                }
                val target = getTargetApi(statement)
                if (target != -1 && api <= target) {
                    return
                }

                if (isSuppressed(context, api, statement, minSdk)) {
                    return
                }

                val location = context.getLocation(typeReference)
                val fqcn = resolved.qualifiedName
                val message =
                    "Class requires API level $api (current min is $minSdk): `$fqcn`"
                report(UNSUPPORTED, typeReference, location, message, apiLevelFix(api), signature)
            }
        }

        override fun visitSwitchExpression(node: USwitchExpression) {
            val expression = node.expression
            if (expression != null) {
                val type = expression.getExpressionType()
                if (type is PsiClassType) {
                    checkClassType(expression, type, "Enum for switch")
                }
            }
        }

        /**
         * Checks a Java source field reference. Returns true if the field is known regardless of
         * whether it's an invalid field or not
         */
        private fun checkField(node: UElement, field: PsiField) {
            val apiDatabase = apiDatabase ?: return
            val type = field.type
            val name = field.name

            if (SDK_INT == name) { // TODO && "android/os/Build$VERSION".equals(owner) ?
                checkObsoleteSdkVersion(context, node)
            }

            val containingClass = field.containingClass ?: return
            val evaluator = context.evaluator
            val owner = evaluator.getQualifiedName(containingClass) ?: return

            // Enforce @RequiresApi
            val modifierList = field.modifierList
            if (!checkRequiresApi(node, field, modifierList)) {
                checkRequiresApi(node, field, containingClass.modifierList)
            }

            val api = apiDatabase.getFieldVersion(owner, name)
            if (api != -1) {
                val minSdk = getMinSdk(context)
                if (api > minSdk && api > getTargetApi(node)) {
                    // Only look for compile time constants. See JLS 15.28 and JLS 13.4.9.
                    var issue = if (evaluator.isStatic(field) && evaluator.isFinal(field))
                        INLINED
                    else
                        UNSUPPORTED
                    if (type !is PsiPrimitiveType && !isString(type)) {
                        issue = UNSUPPORTED

                        // Declaring enum constants are safe; they won't be called on older
                        // platforms.
                        val parent = skipParentheses(node.uastParent)
                        if (parent is USwitchClauseExpression) {
                            val conditions = parent.caseValues

                            if (conditions.contains(node)) {
                                return
                            }
                        }
                    } else if (issue == INLINED && isBenignConstantUsage(node, name, owner)) {
                        return
                    }

                    if (owner == "java.lang.annotation.ElementType") {
                        // TYPE_USE and TYPE_PARAMETER annotations cannot be referenced
                        // on older devices, but it's typically fine to declare these
                        // annotations since they're normally not loaded at runtime; they're
                        // meant for static analysis.
                        val parent: UDeclaration? = node.getParentOfType(
                            parentClass = UDeclaration::class.java,
                            strict = true
                        )
                        if (parent is UClass && parent.isAnnotationType) {
                            return
                        }
                    }

                    val fqcn = getFqcn(owner) + '#'.toString() + name

                    if (isSuppressed(context, api, node, minSdk)) {
                        return
                    }

                    val min = Math.max(minSdk, getTargetApi(node))
                    val message =
                        "Field requires API level $api (current min is $min): `$fqcn`"

                    // If the reference is a qualified expression, don't just highlight the
                    // field name itself; include the qualifiers too
                    var locationNode = node

                    // But only include expressions to the left; for example, if we're
                    // trying to highlight the field "OVERLAY" in
                    //     PorterDuff.Mode.OVERLAY.hashCode()
                    // we should *not* include the .hashCode() suffix
                    while (locationNode.uastParent is UQualifiedReferenceExpression &&
                        (locationNode.uastParent as UQualifiedReferenceExpression)
                            .selector === locationNode
                    ) {
                        locationNode = locationNode.uastParent ?: node
                    }

                    val location = context.getLocation(locationNode)
                    report(issue, node, location, message, apiLevelFix(api), owner, name)
                }
            }
        }
    }

    private fun checkObsoleteSdkVersion(context: JavaContext, node: UElement) {
        val binary = node.getParentOfType<UBinaryExpression>(UBinaryExpression::class.java, true)
        if (binary != null) {
            val minSdk = getMinSdk(context)
            val isConditional = isVersionCheckConditional(minSdk, binary)
            if (isConditional != null) {
                val message = (if (isConditional)
                    "Unnecessary; SDK_INT is always >= "
                else
                    "Unnecessary; SDK_INT is never < ") + minSdk
                context.report(
                    OBSOLETE_SDK,
                    binary,
                    context.getLocation(binary),
                    message,
                    fix().data(isConditional)
                )
            }
        }
    }

    override fun checkFolder(context: ResourceContext, folderName: String) {
        val folderVersion = context.folderVersion
        val minSdkVersion = context.mainProject.minSdkVersion
        if (folderVersion > 1 && folderVersion <= minSdkVersion.featureLevel) {
            val folderConfig =
                FolderConfiguration.getConfigForFolder(folderName) ?: error(context.file)
            folderConfig.versionQualifier = null
            val resourceFolderType = context.resourceFolderType ?: error(context.file)
            val newFolderName = folderConfig.getFolderName(resourceFolderType)
            context.report(
                OBSOLETE_SDK,
                Location.create(context.file),
                "This folder configuration (`v$folderVersion`) is unnecessary; " +
                        "`minSdkVersion` is ${minSdkVersion.apiString}. " +
                        "Merge all the resources in this folder " +
                        "into `$newFolderName`.",
                fix().data(context.file, newFolderName, minSdkVersion)
            )
        }
    }

    companion object {
        @JvmField
        val REQUIRES_API_ANNOTATION = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "RequiresApi")

        private const val SDK_SUPPRESS_ANNOTATION = "android.support.test.filters.SdkSuppress"

        /** Accessing an unsupported API */
        @JvmField
        val UNSUPPORTED = Issue.create(
            id = "NewApi",
            briefDescription = "Calling new methods on older versions",
            explanation = """
                This check scans through all the Android API calls in the application and \
                warns about any calls that are not available on **all** versions targeted by \
                this application (according to its minimum SDK attribute in the manifest).

                If you really want to use this API and don't need to support older devices \
                just set the `minSdkVersion` in your `build.gradle` or `AndroidManifest.xml` \
                files.

                If your code is **deliberately** accessing newer APIs, and you have ensured \
                (e.g. with conditional execution) that this code will only ever be called on \
                a supported platform, then you can annotate your class or method with the \
                `@TargetApi` annotation specifying the local minimum SDK to apply, such as \
                `@TargetApi(11)`, such that this check considers 11 rather than your manifest \
                file's minimum SDK as the required API level.

                If you are deliberately setting `android:` attributes in style definitions, \
                make sure you place this in a `values-v`*NN* folder in order to avoid running \
                into runtime conflicts on certain devices where manufacturers have added \
                custom attributes whose ids conflict with the new ones on later platforms.

                Similarly, you can use tools:targetApi="11" in an XML file to indicate that \
                the element will only be inflated in an adequate context.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = Implementation(
                ApiDetector::class.java,
                EnumSet.of(
                    Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST
                ),
                Scope.JAVA_FILE_SCOPE,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )

        /** Accessing an inlined API on older platforms */
        @JvmField
        val INLINED = Issue.create(
            id = "InlinedApi",
            briefDescription = "Using inlined constants on older versions",
            explanation = """
                This check scans through all the Android API field references in the \
                application and flags certain constants, such as static final integers and \
                Strings, which were introduced in later versions. These will actually be \
                copied into the class files rather than being referenced, which means that \
                the value is available even when running on older devices. In some cases \
                that's fine, and in other cases it can result in a runtime crash or \
                incorrect behavior. It depends on the context, so consider the code carefully \
                and decide whether it's safe and can be suppressed or whether the code needs \
                to be guarded.

                If you really want to use this API and don't need to support older devices \
                just set the `minSdkVersion` in your `build.gradle` or `AndroidManifest.xml` \
                files.

                If your code is **deliberately** accessing newer APIs, and you have ensured \
                (e.g. with conditional execution) that this code will only ever be called on \
                a supported platform, then you can annotate your class or method with the \
                `@TargetApi` annotation specifying the local minimum SDK to apply, such as \
                `@TargetApi(11)`, such that this check considers 11 rather than your manifest \
                file's minimum SDK as the required API level.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        /** Method conflicts with new inherited method */
        @JvmField
        val OVERRIDE = Issue.create(
            id = "Override",
            briefDescription = "Method conflicts with new inherited method",
            explanation = """
                Suppose you are building against Android API 8, and you've subclassed \
                Activity. In your subclass you add a new method called `isDestroyed`(). \
                At some later point, a method of the same name and signature is added to \
                Android. Your method will now override the Android method, and possibly break \
                its contract. Your method is not calling `super.isDestroyed()`, since your \
                compilation target doesn't know about the method.

                The above scenario is what this lint detector looks for. The above example is \
                real, since `isDestroyed()` was added in API 17, but it will be true for \
                **any** method you have added to a subclass of an Android class where your \
                build target is lower than the version the method was introduced in.

                To fix this, either rename your method, or if you are really trying to augment \
                the builtin method if available, switch to a higher build target where you can \
                deliberately add `@Override` on your overriding method, and call `super` if \
                appropriate etc.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        /** Attribute unused on older versions */
        @JvmField
        val UNUSED = Issue.create(
            id = "UnusedAttribute",
            briefDescription = "Attribute unused on older versions",
            explanation = """
                This check finds attributes set in XML files that were introduced in a version \
                newer than the oldest version targeted by your application (with the \
                `minSdkVersion` attribute).

                This is not an error; the application will simply ignore the attribute. \
                However, if the attribute is important to the appearance or functionality of \
                your application, you should consider finding an alternative way to achieve the \
                same result with only available attributes, and then you can optionally create \
                a copy of the layout in a layout-vNN folder which will be used on API NN or \
                higher where you can take advantage of the newer attribute.

                Note: This check does not only apply to attributes. For example, some tags can \
                be unused too, such as the new `<tag>` element in layouts introduced in API 21.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                ApiDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER),
                Scope.RESOURCE_FILE_SCOPE,
                Scope.RESOURCE_FOLDER_SCOPE
            )
        )

        /** Obsolete SDK_INT version check */
        @JvmField
        val OBSOLETE_SDK = Issue.create(
            id = "ObsoleteSdkInt",
            briefDescription = "Obsolete SDK_INT Version Check",
            explanation = """
                This check flags version checks that are not necessary, because the \
                `minSdkVersion` (or surrounding known API level) is already at least as high \
                as the version checked for.

                Similarly, it also looks for resources in `-vNN` folders, such as `values-v14` \
                where the version qualifier is less than or equal to the `minSdkVersion`, \
                where the contents should be merged into the best folder.
                """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        private const val TAG_RIPPLE = "ripple"
        private const val TAG_ANIMATED_SELECTOR = "animated-selector"

        private fun isFrameLayout(
            context: XmlContext,
            tagName: String,
            defaultValue: Boolean
        ): Boolean {
            if (tagName.indexOf('.') == -1) {
                // There are a bunch of built in tags that extend FrameLayout:
                // ScrollView, ViewAnimator, etc.
                val sdkInfo = context.client.getSdkInfo(context.project)
                return sdkInfo.isSubViewOf(FRAME_LAYOUT, tagName)
            }

            // Custom views: we're not sure
            val parser = context.client.getUastParser(context.project)
            val evaluator = parser.evaluator
            val psiClass = evaluator.findClass(tagName) ?: return defaultValue
            return evaluator.extendsClass(psiClass, FQCN_FRAME_LAYOUT, false)
        }

        private fun dependsOnAppCompat(project: Project) =
            TRUE == project.dependsOn(APPCOMPAT_LIB_ARTIFACT)

        private fun apiLevelFix(api: Int): LintFix {
            return LintFix.create().data(api)
        }

        /**
         * Returns true if the view tag is possibly a text view. It may not be certain, but will err on
         * the side of caution (for example, any custom view is considered to be a potential text view.)
         */
        private fun viewMayExtendTextView(element: Element): Boolean {
            var tag: String = element.tagName
            if (tag == VIEW_TAG) {
                tag = element.getAttribute(ATTR_CLASS) ?: return false
                if (tag.isEmpty()) {
                    return false
                }
            }

            return if (tag.indexOf('.') != -1) {
                // Custom views: not sure. Err on the side of caution.
                true
            } else tag.contains("Text") || // TextView, EditText, etc
                    tag.contains(BUTTON) || // Button, ToggleButton, etc
                    tag == "DigitalClock" ||
                    tag == "Chronometer" ||
                    tag == CHECK_BOX ||
                    tag == SWITCH
        }

        /**
         * Returns true if this attribute is in a drawable document with one of the root tags that
         * require API 21
         */
        private fun isAlreadyWarnedDrawableFile(
            context: XmlContext,
            attribute: Attr,
            attributeApiLevel: Int
        ): Boolean {
            // Don't complain if it's in a drawable file where we've already
            // flagged the root drawable type as being unsupported
            if (context.resourceFolderType == ResourceFolderType.DRAWABLE && attributeApiLevel == 21) {
                var element: Element? = attribute.ownerElement
                while (element != null) {
                    // Can't just look at the root document tag: in the middle of the hierarchy
                    // we could have a virtual root via <aapt:attr>
                    val root = element.tagName
                    if (TAG_RIPPLE == root ||
                        TAG_VECTOR == root ||
                        TAG_ANIMATED_VECTOR == root ||
                        TAG_ANIMATED_SELECTOR == root
                    ) {
                        return true
                    }
                    val parentNode = element.parentNode
                    if (parentNode is Element) {
                        element = parentNode
                    } else {
                        break
                    }
                }
            }

            return false
        }

        /**
         * Is the given attribute a "benign" unused attribute, one we probably don't need to flag to the
         * user as not applicable on all versions? These are typically attributes which add some nice
         * platform behavior when available, but that are not critical and developers would not
         * typically need to be aware of to try to implement workarounds on older platforms.
         */
        fun isBenignUnusedAttribute(name: String): Boolean {
            return (ATTR_LABEL_FOR == name ||
                    ATTR_TEXT_IS_SELECTABLE == name ||
                    "textAlignment" == name ||
                    "roundIcon" == name ||
                    ATTR_FULL_BACKUP_CONTENT == name)
        }

        private fun checkSimpleDateFormat(
            context: JavaContext,
            call: UCallExpression,
            minSdk: Int
        ) {
            if (minSdk >= 9) {
                // Already OK
                return
            }

            val expressions = call.valueArguments
            if (expressions.isEmpty()) {
                return
            }
            val argument = expressions[0]
            val constant = ConstantEvaluator.evaluate(context, argument)
            if (constant is String) {
                var isEscaped = false
                for (i in 0 until constant.length) {
                    val c = constant[i]
                    if (c == '\'') {
                        isEscaped = !isEscaped
                    } else if (!isEscaped && (c == 'L' || c == 'c')) {
                        val message =
                            "The pattern character '$c' requires API level 9 (current min is $minSdk) : \"`$constant`\""
                        context.report(
                            UNSUPPORTED,
                            call,
                            context.getRangeLocation(argument, i + 1, 1),
                            message,
                            apiLevelFix(9)
                        )
                        return
                    }
                }
            }
        }

        /**
         * Returns the minimum SDK to use in the given element context, or -1 if no `tools:targetApi` attribute was found.
         *
         * @param element the element to look at, including parents
         * @return the API level to use for this element, or -1
         */
        private fun getLocalMinSdk(element: Element): Int {
            var current = element

            while (true) {
                val targetApi = current.getAttributeNS(TOOLS_URI, ATTR_TARGET_API)
                if (targetApi != null && !targetApi.isEmpty()) {
                    return if (targetApi[0].isDigit()) {
                        try {
                            Integer.parseInt(targetApi)
                        } catch (e: NumberFormatException) {
                            break
                        }
                    } else {
                        SdkVersionInfo.getApiByBuildCode(targetApi, true)
                    }
                }

                val parent = current.parentNode
                if (parent != null && parent.nodeType == Node.ELEMENT_NODE) {
                    current = parent as Element
                } else {
                    break
                }
            }

            return -1
        }

        /**
         * Checks if the current project supports features added in `minGradleVersion` version of
         * the Android gradle plugin.
         *
         * @param context Current context.
         * @param minGradleVersionString Version in which support for a given feature was added, or null
         * if it's not supported at build time.
         */
        private fun featureProvidedByGradle(
            context: XmlContext,
            minGradleVersionString: String?
        ): Boolean {
            if (minGradleVersionString == null) {
                return false
            }

            val gradleModelVersion = context.project.gradleModelVersion
            if (gradleModelVersion != null) {
                val minVersion = GradleVersion.tryParse(minGradleVersionString)
                if (minVersion != null && gradleModelVersion.compareIgnoringQualifiers(minVersion) >= 0) {
                    return true
                }
            }
            return false
        }

        /**
         * Checks whether the given instruction is a benign usage of a constant defined in a later
         * version of Android than the application's `minSdkVersion`.
         *
         * @param node the instruction to check
         * @param name the name of the constant
         * @param owner the field owner
         * @return true if the given usage is safe on older versions than the introduction level of the
         * constant
         */
        fun isBenignConstantUsage(
            node: UElement?,
            name: String,
            owner: String
        ): Boolean {
            if (equivalentName(owner, "android/os/Build\$VERSION_CODES")) {
                // These constants are required for compilation, not execution
                // and valid code checks it even on older platforms
                return true
            }
            if (equivalentName(
                    owner,
                    "android/view/ViewGroup\$LayoutParams"
                ) && name == "MATCH_PARENT"
            ) {
                return true
            }
            if (equivalentName(
                    owner,
                    "android/widget/AbsListView"
                ) && (name == "CHOICE_MODE_NONE" ||
                        name == "CHOICE_MODE_MULTIPLE" ||
                        name == "CHOICE_MODE_SINGLE")
            ) {
                // android.widget.ListView#CHOICE_MODE_MULTIPLE and friends have API=1,
                // but in API 11 it was moved up to the parent class AbsListView.
                // Referencing AbsListView#CHOICE_MODE_MULTIPLE technically requires API 11,
                // but the constant is the same as the older version, so accept this without
                // warning.
                return true
            }

            // Gravity#START and Gravity#END are okay; these were specifically written to
            // be backwards compatible (by using the same lower bits for START as LEFT and
            // for END as RIGHT)
            if (equivalentName(
                    owner,
                    "android/view/Gravity"
                ) && ("START" == name || "END" == name)
            ) {
                return true
            }

            if (node == null) {
                return false
            }

            // It's okay to reference the constant as a case constant (since that
            // code path won't be taken) or in a condition of an if statement
            var curr = node.uastParent
            while (curr != null) {
                if (curr is USwitchClauseExpression) {
                    val caseValues = curr.caseValues
                    for (condition in caseValues) {
                        if (node.isChildOf(condition, false)) {
                            return true
                        }
                    }
                    return false
                } else if (curr is UIfExpression) {
                    val condition = curr.condition
                    return node.isChildOf(condition, false)
                } else if (curr is UMethod || curr is UClass) {
                    break
                }
                curr = curr.uastParent
            }

            return false
        }

        /**
         * Returns the first (in DFS order) inheritance chain connecting the two given classes.
         *
         * @param derivedClass the derived class
         * @param baseClass the base class
         * @return The first found inheritance chain connecting the two classes, or `null` if the
         * classes are not related by inheritance. The `baseClass` is not included in the
         * returned inheritance chain, which will be empty if the two classes are the same.
         */
        private fun getInheritanceChain(
            derivedClass: PsiClassType,
            baseClass: PsiClassType?
        ): List<PsiClassType>? {
            if (derivedClass == baseClass) {
                return emptyList()
            }
            val chain = getInheritanceChain(derivedClass, baseClass, HashSet(), 0)
            chain?.reverse()
            return chain
        }

        private fun getInheritanceChain(
            derivedClass: PsiClassType,
            baseClass: PsiClassType?,
            visited: HashSet<PsiType>,
            depth: Int
        ): MutableList<PsiClassType>? {
            if (derivedClass == baseClass) {
                return ArrayList(depth)
            }

            for (type in derivedClass.superTypes) {
                if (visited.add(type) && type is PsiClassType) {
                    val chain = getInheritanceChain(type, baseClass, visited, depth + 1)
                    if (chain != null) {
                        chain.add(derivedClass)
                        return chain
                    }
                }
            }
            return null
        }

        private fun isSuppressed(
            context: JavaContext,
            api: Int,
            element: UElement,
            minSdk: Int
        ): Boolean {
            if (api <= minSdk) {
                return true
            }
            val target = getTargetApi(element)
            if (target != -1) {
                if (api <= target) {
                    return true
                }
            }

            val driver = context.driver
            return (driver.isSuppressed(context, UNSUPPORTED, element) ||
                    driver.isSuppressed(context, INLINED, element) ||
                    isWithinVersionCheckConditional(context.evaluator, element, api) ||
                    isPrecededByVersionCheckExit(element, api))
        }

        @JvmStatic
        fun getTargetApi(scope: UElement?): Int {
            var current = scope
            while (current != null) {
                if (current is UAnnotated) {
                    val targetApi = getTargetApiForAnnotated(current)
                    if (targetApi != -1) {
                        return targetApi
                    }
                }
                if (current is UFile) {
                    break
                }
                current = current.uastParent
            }

            return -1
        }

        /**
         * Returns the API level for the given AST node if specified with an `@TargetApi`
         * annotation.
         *
         * @param annotated the annotated element to check
         * @return the target API level, or -1 if not specified
         */
        private fun getTargetApiForAnnotated(annotated: UAnnotated?): Int {
            if (annotated == null) {
                return -1
            }

            for (annotation in annotated.annotations) {
                val fqcn = annotation.qualifiedName
                if (fqcn != null && (fqcn == FQCN_TARGET_API ||
                            REQUIRES_API_ANNOTATION.isEquals(fqcn) ||
                            fqcn == SDK_SUPPRESS_ANNOTATION ||
                            fqcn == TARGET_API)
                ) { // when missing imports
                    val attributeList = annotation.attributeValues
                    for (attribute in attributeList) {
                        val expression = attribute.expression
                        if (expression is ULiteralExpression) {
                            val value = expression.value
                            if (value is Int) {
                                return value
                            } else if (value is String) {
                                return codeNameToApi(value)
                            }
                        } else if (expression is UCallExpression) {
                            for (argument in expression.valueArguments) {
                                if (argument is ULiteralExpression) {
                                    val value = argument.value
                                    if (value is Int) {
                                        return value
                                    } else if (value is String) {
                                        return codeNameToApi(value)
                                    }
                                }
                            }
                        } else {
                            val apiLevel = ConstantEvaluator.evaluate(null, expression) as? Int
                            if (apiLevel != null) {
                                return apiLevel
                            } else if (expression is UReferenceExpression) {
                                val name = expression.resolvedName
                                if (name != null) {
                                    return codeNameToApi(name)
                                }
                            } else {
                                return codeNameToApi(expression.asSourceString())
                            }
                        }
                    }
                } else if (fqcn == null) {
                    // Work around bugs in UAST type resolution for file annotations:
                    // parse the source string instead.
                    val psi = annotation.sourcePsi ?: continue
                    if (psi is PsiCompiledElement) {
                        continue
                    }
                    val text = psi.text
                    if (text.contains("TargetApi(") ||
                        text.contains("RequiresApi(") ||
                        text.contains("SdkSuppress(")
                    ) {
                        val start = text.indexOf('(')
                        val end = text.indexOf(')', start + 1)
                        if (end != -1) {
                            var name = text.substring(start + 1, end)
                            // Strip off attribute name and qualifiers, e.g.
                            //   @RequiresApi(api = Build.VERSION.O) -> O
                            var index = name.indexOf('=')
                            if (index != -1) {
                                name = name.substring(index + 1).trim()
                            }
                            index = name.indexOf('.')
                            if (index != -1) {
                                name = name.substring(index + 1)
                            }
                            if (!name.isEmpty()) {
                                if (name[0].isDigit()) {
                                    val api = Integer.parseInt(name)
                                    if (api > 0) {
                                        return api
                                    }
                                } else {
                                    return codeNameToApi(name)
                                }
                            }
                        }
                    }
                }
            }

            return -1
        }

        fun getCatchParametersLocation(
            context: JavaContext,
            catchClause: UCatchClause
        ): Location {
            val types = catchClause.typeReferences
            if (types.isEmpty()) {
                return Location.NONE
            }

            val first = context.getLocation(types[0])
            if (types.size < 2) {
                return first
            }

            val last = context.getLocation(types[types.size - 1])
            val file = first.file
            val start = first.start
            val end = last.end

            return if (start == null) {
                Location.create(file)
            } else Location.create(file, start, end)
        }

        fun isMultiCatchReflectiveOperationException(catchClause: UCatchClause): Boolean {
            val types = catchClause.types
            if (types.size < 2) {
                return false
            }

            for (t in types) {
                if (!isSubclassOfReflectiveOperationException(t)) {
                    return false
                }
            }

            return true
        }

        private const val REFLECTIVE_OPERATION_EXCEPTION = "java.lang.ReflectiveOperationException"

        private fun isSubclassOfReflectiveOperationException(type: PsiType): Boolean {
            for (t in type.superTypes) {
                if (REFLECTIVE_OPERATION_EXCEPTION == t.canonicalText) {
                    return true
                }
            }
            return false
        }
    }
}
