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

import com.android.SdkConstants.ANDROID_PKG
import com.android.SdkConstants.ANDROID_PREFIX
import com.android.SdkConstants.ANDROID_THEME_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.ATTR_AUTOFILL_HINTS
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_FOREGROUND
import com.android.SdkConstants.ATTR_FULL_BACKUP_CONTENT
import com.android.SdkConstants.ATTR_HEIGHT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL
import com.android.SdkConstants.ATTR_LABEL_FOR
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_ROUND_ICON
import com.android.SdkConstants.ATTR_TARGET_API
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.SdkConstants.ATTR_TEXT_IS_SELECTABLE
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.ATTR_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.FQCN_FRAME_LAYOUT
import com.android.SdkConstants.FQCN_TARGET_API
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.SdkConstants.TAG
import com.android.SdkConstants.TAG_ANIMATED_VECTOR
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.TAG_VECTOR
import com.android.SdkConstants.TARGET_API
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_TAG
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.resourceNameToFieldName
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.sdklib.SdkVersionInfo
import com.android.support.AndroidxName
import com.android.tools.lint.checks.ApiLookup.equivalentName
import com.android.tools.lint.checks.ApiLookup.startsWithEquivalentPrefix
import com.android.tools.lint.checks.RtlDetector.ATTR_SUPPORTS_RTL
import com.android.tools.lint.checks.VersionChecks.Companion.SDK_INT
import com.android.tools.lint.checks.VersionChecks.Companion.codeNameToApi
import com.android.tools.lint.checks.VersionChecks.Companion.getVersionCheckConditional
import com.android.tools.lint.checks.VersionChecks.Companion.isPrecededByVersionCheckExit
import com.android.tools.lint.checks.VersionChecks.Companion.isWithinVersionCheckConditional
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext.Companion.getFqcn
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceFolderScanner
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getLongAttribute
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.tools.lint.detector.api.getChildren
import com.android.tools.lint.detector.api.getInternalMethodName
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.isString
import com.android.tools.lint.detector.api.skipParentheses
import com.android.utils.XmlUtils
import com.android.utils.usLocaleCapitalize
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
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
import org.jetbrains.uast.UPolyadicExpression
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
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.java.JavaUAnnotation
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isInstanceCheck
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.util.isTypeCast
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.lang.Boolean.TRUE
import java.util.ArrayList
import java.util.EnumSet
import kotlin.math.max
import kotlin.math.min

/**
 * Looks for usages of APIs that are not supported in all the versions
 * targeted by this application (according to its minimum API
 * requirement in the manifest).
 */
class ApiDetector : ResourceXmlDetector(), SourceCodeScanner, ResourceFolderScanner {
    private var apiDatabase: ApiLookup? = null

    override fun beforeCheckRootProject(context: Context) {
        if (apiDatabase == null) {
            apiDatabase = ApiLookup.get(context.client, context.project.buildTarget)
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

    override fun getApplicableElements(): Collection<String> {
        return XmlScannerConstants.ALL
    }

    override fun getApplicableAttributes(): Collection<String> {
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
                (
                    (!isAttributeOfGradientOrGradientItem(attribute) && name != "fillType") ||
                        !dependsOnAppCompat(context.project)
                    )
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
                        //
                        // However, paddingStart in particular is known to cause crashes
                        // when used on TextViews (and subclasses of TextViews), on some
                        // devices, because vendor specific attributes conflict with the
                        // later-added framework resources, and these are apparently read
                        // by the text views.
                        //
                        // However, as of build tools 23.0.1 aapt works around this by packaging
                        // the resources differently. At this point everyone is using a newer
                        // version of aapt.
                    } else {
                        val location = context.getLocation(attribute)
                        val localName = attribute.localName
                        var message =
                            "Attribute `$localName` is only used in API level $attributeApiLevel and higher (current min is %1\$d)"

                        // Supported by appcompat
                        if ("fontFamily" == localName) {
                            if (dependsOnAppCompat(context.project)) {
                                val prefix = XmlUtils.lookupNamespacePrefix(
                                    attribute, AUTO_URI, "app", false
                                )
                                message += " Did you mean `$prefix:fontFamily` ?"
                            }
                        }
                        report(context, UNUSED, attribute, location, message, attributeApiLevel, minSdk)
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
                if (max(minSdk, context.folderVersion) < 23) {
                    val location = context.getLocation(attribute)
                    val message =
                        "Attribute `android:theme` is only used by `<include>` tags in API level 23 and higher (current min is %1\$d)"
                    report(context, UNUSED, attribute, location, message, 23, minSdk)
                }
            }

            if (name == ATTR_FOREGROUND &&
                context.resourceFolderType == ResourceFolderType.LAYOUT &&
                !isFrameLayout(context, attribute.ownerElement.tagName, true)
            ) {
                // Requires API 23, unless it's a FrameLayout
                val minSdk = getMinSdk(context)
                if (max(minSdk, context.folderVersion) < 23) {
                    val location = context.getLocation(attribute)
                    val message =
                        "Attribute `android:foreground` has no effect on API levels lower than 23 (current min is %1\$d)"
                    report(context, UNUSED, attribute, location, message, 23, minSdk)
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
                        "Using theme references in XML drawables requires API level $api (current min is %1\$d)"
                    report(context, UNSUPPORTED, attribute, location, message, api, minSdk)
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
            name = resourceNameToFieldName(value.substring(PREFIX_ANDROID.length))
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
                    name = resourceNameToFieldName(value.substring(index + 1))
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
                        "`$name` requires API level $api (current min is %1\$d), but note " +
                            "that attribute `$attributeName` is only used in API level " +
                            "$attributeApiLevel and higher"
                    report(context, UNSUPPORTED, attribute, location, message, api, minSdk)
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
                        "`$value` requires API level $api (current min is %1\$d)"
                    report(context, UNSUPPORTED, attribute, location, message, api, minSdk)
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
                                val name = resourceNameToFieldName(text.substring(index + 1))
                                val api = apiDatabase.getFieldVersion(owner, name)
                                val minSdk = getMinSdk(context)
                                if (api > minSdk &&
                                    api > context.folderVersion &&
                                    api > getLocalMinSdk(element)
                                ) {
                                    val location = context.getLocation(textNode)
                                    val message =
                                        "`$text` requires API level $api (current min is %1\$d)"
                                    report(context, UNSUPPORTED, element, location, message, api, minSdk)
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
            if (api > minSdk && api > context.folderVersion) {
                val localMinSdk = getLocalMinSdk(element)
                if (api > localMinSdk) {
                    val location = context.getNameLocation(element)
                    val message =
                        "View requires API level $api (current min is %1\$d): `<$tag>`"
                    report(context, UNSUPPORTED, element, location, message, api, localMinSdk)
                }
            }
        }
    }

    /**
     * Checks whether the given element is the given tag, and if so,
     * whether it satisfied the minimum version that the given tag is
     * supported in.
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
                                "Attribute `${attribute.localName}` is only used in API level $attributeApiLevel and higher (current min is %1\$d)"
                            report(context, UNUSED, attribute, location, message, attributeApiLevel, minSdk)
                        }
                    }
                }
            }
            curr = curr.nextSibling
        }
    }

    /**
     * Checks whether the given element is the given tag, and if so,
     * whether it satisfied the minimum version that the given tag is
     * supported in.
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
                        "`<$realTag>` requires API level $api (current min is %1\$d)"
                    if (gradleVersion != null) {
                        message +=
                            " or building with Android Gradle plugin $gradleVersion or higher"
                    } else if (realTag.contains(".")) {
                        message =
                            "Custom drawables requires API level $api (current min is %1\$d)"
                    }
                } else {
                    assert(issue === UNUSED) { issue }
                    message =
                        "`<$realTag>` is only used in API level $api and higher (current min is %1\$d)"
                }
                report(context, issue, element, location, message, api, minSdk)
            }
        }
    }

    private fun getMinSdk(context: Context): Int {
        val project = if (context.isGlobalAnalysis())
            context.mainProject else context.project
        return if (!project.isAndroidProject) {
            // Don't flag API checks in non-Android projects
            Integer.MAX_VALUE
        } else {
            project.minSdkVersion.featureLevel
        }
    }

    // ---- implements SourceCodeScanner ----

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        if (apiDatabase == null || context.isTestSource && !context.driver.checkTestSources) {
            return null
        }
        val project = if (context.isGlobalAnalysis())
            context.mainProject else context.project
        return if (project.isAndroidProject) {
            ApiVisitor(context)
        } else {
            null
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
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
            UCallableReferenceExpression::class.java,
            UArrayAccessExpression::class.java
        )
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val mainProject = context.mainProject
        val mainMinSdk = if (!mainProject.isAndroidProject) {
            // Don't flag API checks in non-Android projects
            Integer.MAX_VALUE
        } else {
            mainProject.minSdkVersion.featureLevel
        }

        val requires = map.getInt(KEY_REQUIRES_API) ?: return false
        if (requires <= mainMinSdk) {
            return false
        }

        val target = map.getInt(KEY_MIN_API) ?: return false
        val minSdk = max(target, mainMinSdk)
        val desugaring = map.getInt(KEY_DESUGAR, null)?.let {
            Desugaring.fromConstant(it)
        }
        if (desugaring != null && mainProject.isDesugaring(desugaring)) {
            // See if library desugaring is turned on in the main project
            if (desugaring == Desugaring.JAVA_8_LIBRARY) {
                val owner = map.getString(KEY_OWNER, null)
                if (owner != null) {
                    val name = map.getString(KEY_NAME)
                    if (isLibraryDesugared(context, owner, name)) {
                        return false
                    }
                }
            } else {
                return false
            }
        }

        // Update the minSdkVersion included in the message
        val formatString = map.getString(KEY_MESSAGE) ?: return false
        incident.message = String.format(formatString, minSdk)
        return true
    }

    private fun isLibraryDesugared(context: Context, owner: String?, name: String?): Boolean {
        val project = if (context.isGlobalAnalysis())
            context.mainProject else context.project
        if (owner != null && (owner.startsWith("java/") || owner.startsWith("java.")) &&
            project.isDesugaring(Desugaring.JAVA_8_LIBRARY) &&
            isApiDesugared(project, owner.replace('/', '.'), name)
        ) {
            return true
        }
        return false
    }

    private fun report(
        context: XmlContext,
        issue: Issue,
        scope: Node,
        location: Location,
        message: String,
        api: Int,
        minSdk: Int
    ) {
        assert(message.contains("%1\$d"))
        val incident = Incident(
            issue = issue,
            message = "", // always formatted in accept before reporting
            location = location,
            scope = scope,
            fix = apiLevelFix(api)
        )
        val map = map().apply {
            put(KEY_REQUIRES_API, api)
            put(KEY_MESSAGE, message)

            val localMinSdk = if (minSdk == -1) {
                val element = when (scope) {
                    is Attr -> scope.ownerElement
                    is Element -> scope
                    else -> scope.ownerDocument.documentElement
                }
                getLocalMinSdk(element)
            } else {
                minSdk
            }
            put(KEY_MIN_API, localMinSdk)
        }
        context.report(incident, map)
    }

    private inner class ApiVisitor(private val context: JavaContext) : UElementHandler() {

        private fun report(
            issue: Issue,
            node: UElement,
            location: Location,
            type: String,
            sig: String,
            requires: Int,
            minSdk: Int,
            fix: LintFix? = null,
            owner: String? = null,
            name: String? = null,
            @Suppress("UNUSED_PARAMETER")
            desc: String? = null,
            desugaring: Desugaring? = null
        ) {
            val apiLevel = getApiLevelString(requires)
            val typeString = type.usLocaleCapitalize()
            val formatString = "$typeString requires API level $apiLevel (current min is %1\$s): `$sig`"
            report(issue, node, location, formatString, fix, owner, name, desc, requires, minSdk, desugaring)
        }

        private fun report(
            issue: Issue,
            node: UElement,
            location: Location,
            formatString: String, // one parameter: minSdkVersion
            fix: LintFix? = null,
            owner: String? = null,
            name: String? = null,
            @Suppress("UNUSED_PARAMETER")
            desc: String? = null,
            requires: Int,
            min: Int = 1,
            desugaring: Desugaring? = null
        ) {
            // Java 8 API desugaring?
            if (isLibraryDesugared(context, owner, name)) {
                return
            }
            val incident = Incident(
                issue = issue,
                message = "", // always formatted in accept() before reporting
                location = location,
                scope = node,
                fix = fix
            )
            val map = map().apply {
                put(KEY_REQUIRES_API, requires)
                put(KEY_MIN_API, max(min, getTargetApi(node)))
                put(KEY_MESSAGE, formatString)
                if (owner != null && canBeDesugaredLater(owner, name)) {
                    put(KEY_OWNER, owner)
                    name?.let { put(KEY_NAME, it) }
                }
                if (desugaring != null) {
                    put(KEY_DESUGAR, desugaring.constant)
                }
            }
            context.report(incident, map)
        }

        /**
         * Returns true if this looks like a reference that can be
         * desugared in a consuming library. This will return true
         * if the API is in a package known to be related to library
         * desugaring, and library desugaring is **not** turned on for
         * this library.
         */
        private fun canBeDesugaredLater(owner: String?, name: String?): Boolean {
            val project = if (context.isGlobalAnalysis())
                context.mainProject else context.project
            return owner != null &&
                (owner.startsWith("java/") || owner.startsWith("java.")) &&
                !project.isDesugaring(Desugaring.JAVA_8_LIBRARY)
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
            report(
                UNSUPPORTED,
                expression,
                location,
                "Method reference",
                signature,
                api,
                minSdk,
                apiLevelFix(api),
                owner,
                name,
                desc
            )
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

            val location = context.getLocation(node)
            report(
                UNSUPPORTED,
                node,
                location,
                "Class",
                expressionOwner,
                api,
                minSdk,
                apiLevelFix(api),
                expressionOwner
            )
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

            // Also see if this cast has been explicitly checked for
            var curr = node
            while (true) {
                val check = curr.getParentOfType(UIfExpression::class.java, true, UMethod::class.java)
                    ?: break
                val condition = check.condition
                if (condition is UBinaryExpressionWithType) {
                    val type = condition.type
                    // Explicitly checked with surrounding instanceof check
                    if (type == interfaceType) {
                        return
                    }
                }

                curr = check
            }

            val location = context.getLocation(node)
            val message: String
            val to = interfaceType.className
            val from = classType.className
            message = if (interfaceTypeInternal == classTypeInternal) {
                "Cast to `$to` requires API level $api (current min is %1\$d)"
            } else {
                "Cast from `$from` to `$to` requires API level $api (current min is %1\$d)"
            }

            report(
                UNSUPPORTED, node, location, message, apiLevelFix(api), classTypeInternal,
                requires = api, min = minSdk
            )
        }

        override fun visitMethod(node: UMethod) {
            val apiDatabase = apiDatabase ?: return
            val containingClass = node.containingClass

            // API check for default methods
            if (containingClass != null &&
                containingClass.isInterface &&
                // (unless using desugar which supports this for all API levels)
                !context.project.isDesugaring(Desugaring.INTERFACE_METHODS)
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
                            "Default method"
                        else
                            "Static interface method"
                        report(
                            UNSUPPORTED,
                            node,
                            location,
                            desc,
                            containingClass.name + "#" + node.name,
                            api,
                            minSdk,
                            apiLevelFix(api),
                            containingClass.qualifiedName,
                            desugaring = Desugaring.INTERFACE_METHODS
                        )
                    }
                }
            }

            val buildSdk = context.project.buildSdk
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
                                fqcn = "$className#$name"
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
                            context.report(Incident(OVERRIDE, node, location, message))
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
                !context.project.isDesugaring(Desugaring.TYPE_ANNOTATIONS)
            ) {
                val evaluator = context.evaluator
                for (
                    annotation in evaluator.getAllAnnotations(
                        node as PsiModifierListOwner,
                        false
                    )
                ) {
                    val name = annotation.qualifiedName
                    if ("java.lang.annotation.Repeatable" == name) {
                        val api = 24 // minSdk for repeatable annotations
                        val minSdk = getMinSdk(context)
                        if (!isSuppressed(context, api, node, minSdk)) {
                            val location = context.getLocation(annotation)
                            val min = max(minSdk, getTargetApi(node))
                            val incident = Incident(
                                issue = UNSUPPORTED,
                                message = "", // always formatted in accept() before reporting
                                location = location,
                                scope = annotation,
                                fix = apiLevelFix(api)
                            )
                            val map = map().apply {
                                put(KEY_REQUIRES_API, api)
                                put(KEY_MIN_API, min)
                                put(KEY_MESSAGE, "Repeatable annotation requires API level $api (current min is %1\$d)")
                                put(KEY_DESUGAR, Desugaring.TYPE_ANNOTATIONS.constant)
                            }
                            context.report(incident, map)
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
            val desc = descriptor ?: "Class"
            report(UNSUPPORTED, element, location, desc, fqcn, api, minSdk, apiLevelFix(api), owner)
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
                var message =
                    "The type of the for loop iterated value is " +
                        "${type.canonicalText}, which requires API level $api" +
                        " (current min is %1\$d)"

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
                report(
                    UNSUPPORTED, node, location, message, apiLevelFix(api), expressionOwner,
                    requires = api, min = minSdk
                )
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
                        checkRequiresApi(node, resolved, resolved)
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
            if (!checkRequiresApi(reference, method, method)) {
                checkRequiresApi(reference, method, containingClass)
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
                includeName = false,
                includeReturn = false
            ) // Couldn't compute description of method for some reason; probably
                // failure to resolve parameter types
                ?: return

            if (call != null &&
                startsWithEquivalentPrefix(owner, "java/text/SimpleDateFormat") &&
                name == CONSTRUCTOR_NAME &&
                desc != "()V"
            ) {
                checkSimpleDateFormat(context, call, getMinSdk(context))
            } else if (call != null &&
                name == "loadAnimator" &&
                owner == "android.animation.AnimatorInflater" &&
                desc == "(Landroid.content.Context;I)"
            ) {
                checkAnimator(context, call)
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
                                        api = min(specificApi, api)
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
                            api = min(specificApi, api)
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
                            //noinspection LintImplPsiEquals
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
            var desugaring: Desugaring? = null
            if (name == "compare" &&
                api == 19 &&
                startsWithEquivalentPrefix(owner, "java/lang/") &&
                desc.length == 4 &&
                (
                    desc == "(JJ)" ||
                        desc == "(ZZ)" ||
                        desc == "(BB)" ||
                        desc == "(CC)" ||
                        desc == "(II)" ||
                        desc == "(SS)"
                    )
            ) {
                if (context.project.isDesugaring(Desugaring.LONG_COMPARE)) {
                    return
                } else {
                    desugaring = Desugaring.LONG_COMPARE
                }
            }

            // Desugar rewrites Objects.requireNonNull calls (see b/32446315)
            if (name == "requireNonNull" &&
                api == 19 &&
                owner == "java.util.Objects" &&
                desc == "(Ljava.lang.Object;)"
            ) {
                if (context.project.isDesugaring(Desugaring.OBJECTS_REQUIRE_NON_NULL)) {
                    return
                } else {
                    desugaring = Desugaring.OBJECTS_REQUIRE_NON_NULL
                }
            }

            if (name == "addSuppressed" &&
                api == 19 &&
                owner == "java.lang.Throwable" &&
                desc == "(Ljava.lang.Throwable;)"
            ) {
                if (context.project.isDesugaring(Desugaring.TRY_WITH_RESOURCES)) {
                    return
                } else {
                    desugaring = Desugaring.TRY_WITH_RESOURCES
                }
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
            report(
                UNSUPPORTED,
                reference,
                location,
                "Call",
                signature,
                api,
                minSdk,
                apiLevelFix(api),
                owner,
                name,
                desc,
                desugaring
            )
        }

        private fun checkAnimator(context: JavaContext, call: UCallExpression) {
            val resourceParameter = call.valueArguments[1]
            val resource = ResourceReference.get(resourceParameter) ?: return
            if (resource.`package` == ANDROID_PKG) {
                return
            }

            val api = 21
            if (getMinSdk(context) >= api) {
                return
            }
            if (isWithinVersionCheckConditional(context, call, api) ||
                isPrecededByVersionCheckExit(context, call, api)
            ) {
                return
            }

            // See if the associated resource references propertyValuesHolder, and if so
            // suggest switching to AnimatorInflaterCompat.loadAnimator.
            val client = context.client
            val full = context.isGlobalAnalysis()
            val project = if (full) context.mainProject else context.project
            val resources = client.getResources(project, LOCAL_DEPENDENCIES)
            val items =
                resources.getResources(ResourceNamespace.TODO(), resource.type, resource.name)
            val paths = items.asSequence().mapNotNull { it.source }.toSet()
            for (path in paths) {
                try {
                    val parser = client.createXmlPullParser(path) ?: continue
                    while (true) {
                        val event = parser.next()
                        if (event == XmlPullParser.START_TAG) {
                            val name = parser.name ?: continue
                            if (name == ATTR_PROPERTY_VALUES_HOLDER) {
                                // It's okay if in a -v21+ folder
                                path.toFile()?.parentFile?.name?.let { nae ->
                                    FolderConfiguration.getConfigForFolder(nae)?.let { config ->
                                        val versionQualifier = config.versionQualifier
                                        if (versionQualifier != null && versionQualifier.version >= api) {
                                            return
                                        }
                                    }
                                }

                                context.report(
                                    UNSUPPORTED, call, context.getLocation(call),
                                    "The resource `${resource.type}.${resource.name}` includes " +
                                        "the tag `$ATTR_PROPERTY_VALUES_HOLDER` which causes crashes " +
                                        "on API < $api. Consider switching to " +
                                        "`AnimatorInflaterCompat.loadAnimator` to safely load the " +
                                        "animation."
                                )
                                return
                            }
                        } else if (event == XmlPullParser.END_DOCUMENT) {
                            return
                        }
                    }
                } catch (ignore: XmlPullParserException) {
                    // Users might be editing these files in the IDE; don't flag
                } catch (ignore: IOException) {
                    // Users might be editing these files in the IDE; don't flag
                }
            }
        }

        private fun getRequiresApiFromAnnotations(modifierListOwner: PsiModifierListOwner): Int {
            for (annotation in context.evaluator.getAllAnnotations(modifierListOwner, false)) {
                val qualifiedName = annotation.qualifiedName
                if (REQUIRES_API_ANNOTATION.isEquals(qualifiedName)) {
                    val wrapped = JavaUAnnotation.wrap(annotation)
                    var api = getLongAttribute(context, wrapped, ATTR_VALUE, -1).toInt()
                    if (api <= 1) {
                        // @RequiresApi has two aliasing attributes: api and value
                        api = getLongAttribute(context, wrapped, "api", -1).toInt()
                    } else if (api == SdkVersionInfo.CUR_DEVELOPMENT) {
                        val version = context.project.buildTarget?.version
                        if (version != null && version.isPreview) {
                            return version.featureLevel
                        }
                        // Special value defined in the Android framework to indicate current development
                        // version. This is different from the tools where we use current stable + 1 since
                        // that's the anticipated version.
                        api = if (SdkVersionInfo.HIGHEST_KNOWN_API > SdkVersionInfo.HIGHEST_KNOWN_STABLE_API) {
                            SdkVersionInfo.HIGHEST_KNOWN_API
                        } else {
                            SdkVersionInfo.HIGHEST_KNOWN_API + 1
                        }

                        // Try to match it up by codename
                        val value = annotation.findDeclaredAttributeValue(ATTR_VALUE)
                            ?: annotation.findDeclaredAttributeValue("api")
                        if (value is PsiReferenceExpression) {
                            val name = value.referenceName
                            if (name?.length == 1) {
                                api = max(api, SdkVersionInfo.getApiByBuildCode(name, true))
                            }
                        }
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
                            if (name.isNotEmpty()) {
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
            modifierListOwner: PsiModifierListOwner?
        ): Boolean {
            modifierListOwner ?: return false

            val api = getRequiresApiFromAnnotations(modifierListOwner)
            if (api != -1) {
                val minSdk = getMinSdk(context)
                if (api > minSdk) {
                    val target = getTargetApi(expression)
                    if (target == -1 || api > target) {
                        if (isWithinVersionCheckConditional(context, expression, api)) {
                            return true
                        }
                        if (isPrecededByVersionCheckExit(context, expression, api)) {
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

                        report(
                            UNSUPPORTED,
                            expression,
                            location,
                            "Call",
                            fqcn,
                            api,
                            minSdk,
                            apiLevelFix(api)
                        )
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

            if (interfaceType !is PsiClassType) {
                return
            }

            if (initializerType == interfaceType) {
                return
            }

            checkCast(initializer, initializerType, interfaceType)
        }

        override fun visitArrayAccessExpression(node: UArrayAccessExpression) {
            val receiver = node.receiver
            val type = receiver.getExpressionType() ?: return
            if (type !is PsiClassType) { // for normal arrays this is typically PsiArrayType
                return
            }

            // No UAST accessor method to find the corresponding get/set methods; see
            // https://youtrack.jetbrains.com/issue/KT-46045
            // Instead we'll search ourselves.
            val clz = type.resolve() ?: return
            val parent = node.uastParent as? UBinaryExpression
            val setter = parent != null && parent.isAssignment()
            if (setter) {
                for (method in clz.findMethodsByName("set", true)) {
                    val parameters = method.parameterList
                    // Here we can also check that the referenced type in the assignment
                    // is the same as getParameter(1) but this is probably overkill;
                    // once KT-46045 this will be moot
                    if (parameters.parametersCount == 2 &&
                        parameters.getParameter(0)?.type == PsiType.INT
                    ) {
                        visitCall(method, null, node)
                        break
                    }
                }
            } else {
                for (method in clz.findMethodsByName("get", true)) {
                    val parameters = method.parameterList
                    if (parameters.parametersCount == 1 &&
                        parameters.getParameter(0)?.type == PsiType.INT
                    ) {
                        visitCall(method, null, node)
                        break
                    }
                }
            }
        }

        override fun visitBinaryExpression(node: UBinaryExpression) {
            // Overloaded operators
            val method = node.resolveOperator()
            if (method != null) {
                visitCall(method, null, node)
            }

            val operator = node.operator
            if (operator is UastBinaryOperator.AssignOperator) {
                // Plain assignment: check casts
                val rExpression = node.rightOperand
                val rhsType = rExpression.getExpressionType() as? PsiClassType ?: return

                val interfaceType = node.leftOperand.getExpressionType()
                if (interfaceType !is PsiClassType) {
                    return
                }

                if (rhsType == interfaceType) {
                    return
                }

                checkCast(rExpression, rhsType, interfaceType)
            }
        }

        override fun visitTryExpression(node: UTryExpression) {
            val resourceList = node.resourceVariables

            if (resourceList.isNotEmpty() &&
                // (unless using desugar which supports this for all API levels)
                !context.project.isDesugaring(Desugaring.TRY_WITH_RESOURCES)
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

                    val message =
                        "Try-with-resources requires API level $api (current min is %1\$d)"
                    report(
                        UNSUPPORTED, node, location, message, apiLevelFix(api),
                        requires = api, min = minSdk, desugaring = Desugaring.TRY_WITH_RESOURCES
                    )
                }
            }

            for (catchClause in node.catchClauses) {
                // Special case reflective operation exception which can be implicitly used
                // with multi-catches: see issue 153406
                val minSdk = getMinSdk(context)
                if (minSdk < 19 && isMultiCatchReflectiveOperationException(catchClause)) {
                    // No -- see 131349148: Dalvik: java.lang.VerifyError
                    if (isSuppressed(context, 19, node, minSdk)) {
                        return
                    }

                    val message =
                        "Multi-catch with these reflection exceptions requires API level 19 (current min is %1\$d) " +
                            "because they get compiled to the common but new super type `ReflectiveOperationException`. " +
                            "As a workaround either create individual catch statements, or catch `Exception`."

                    report(
                        UNSUPPORTED,
                        node,
                        getCatchParametersLocation(context, catchClause),
                        message,
                        apiLevelFix(19),
                        min = minSdk,
                        requires = 19
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

                val containingClass: UClass? = statement.getContainingUClass()
                if (containingClass != null) {
                    val target =
                        if (minSdk < 19)
                        // We only consider @RequiresApi annotations for filtering applicable
                        // minSdkVersion here, not @TargetApi or @SdkSuppress since we need to
                        // communicate outwards that this is a problem; class loading alone, not
                        // just executing the code, is enough to trigger a crash.
                            getTargetApi(containingClass, ::isRequiresApiAnnotation)
                        else
                            getTargetApi(statement)
                    if (target != -1 && api <= target) {
                        return
                    }
                }

                if (isSuppressed(context, api, statement, minSdk)) {
                    // Normally having a surrounding version check is enough, but on Dalvik
                    // just loading the class, whether or not the try statement is ever
                    // executed will result in a crash, so the only way to prevent the
                    // crash there is to never load the class; e.g. mark the whole class
                    // with @RequiresApi:
                    if (minSdk < 19) {
                        // TODO: Look for RequiresApi on the class
                        val location = context.getLocation(typeReference)
                        val fqcn = resolved.qualifiedName
                        val apiLevel = getApiLevelString(api)
                        val apiMessage =
                            "${"Exception".usLocaleCapitalize()} requires API level $apiLevel (current min is %1\$d): `${fqcn ?: ""}`"
                        val message = "$apiMessage, and having a surrounding/preceding version " +
                            "check **does not** help since prior to API level 19, just " +
                            "**loading** the class will cause a crash. Consider marking the " +
                            "surrounding class with `RequiresApi(19)` to ensure that the " +
                            "class is never loaded except when on API 19 or higher."
                        val fix = fix().data(KEY_REQUIRES_API, api, KEY_REQUIRE_CLASS, true)
                        val clause = typeReference.uastParent as? UCatchClause
                        if (clause != null && context.driver.isSuppressed(
                                context,
                                UNSUPPORTED,
                                clause
                            )
                        ) {
                            return
                        }

                        report(
                            UNSUPPORTED, typeReference, location, message, fix, signature,
                            requires = 19, min = minSdk
                        )
                        return
                    } else {
                        // On ART we're good.
                        return
                    }
                }

                val location = context.getLocation(typeReference)
                val fqcn = resolved.qualifiedName
                val fix =
                    if (minSdk < 19) {
                        fix().data(KEY_REQUIRES_API, api, KEY_REQUIRE_CLASS, true)
                    } else {
                        fix().data(KEY_REQUIRES_API, api)
                    }
                report(
                    UNSUPPORTED,
                    typeReference,
                    location,
                    "Exception",
                    fqcn ?: "",
                    api,
                    minSdk,
                    fix,
                    signature
                )
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
         * Checks a Java source field reference. Returns true if the
         * field is known regardless of whether it's an invalid field or
         * not.
         */
        private fun checkField(node: UElement, field: PsiField) {
            val apiDatabase = apiDatabase ?: return
            val type = field.type
            val name = field.name

            val containingClass = field.containingClass ?: return
            val evaluator = context.evaluator
            var owner = evaluator.getQualifiedName(containingClass) ?: return

            if (SDK_INT == name && "android.os.Build.VERSION" == owner) {
                checkObsoleteSdkVersion(context, node)
            }

            // Enforce @RequiresApi
            if (!checkRequiresApi(node, field, field)) {
                checkRequiresApi(node, field, containingClass)
            }

            var api = apiDatabase.getFieldVersion(owner, name)
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

                    if (isSuppressed(context, api, node, minSdk)) {
                        return
                    }

                    // Look to see if it's a field reference for a specific sub class
                    // or interface which defined the field or constant at an earlier
                    // API level.
                    //
                    // For example, for api 28/29 and android.app.TaskInfo,
                    // A number of fields were moved up from ActivityManager.RecentTaskInfo
                    // to the new class TaskInfo in Q; however, these field are almost
                    // always accessed via ActivityManager#taskInfo which is still
                    // a RecentTaskInfo so this code works prior to Q. If you explicitly
                    // access it as a TaskInfo the class reference itself will be
                    // flagged by lint. (The platform change was in
                    // Change-Id: Iaf1731002196bb89319de141a05ab92a7dcb2928)
                    // We can't just unconditionally exit here, since there are existing
                    // API requirements on various fields in the TaskInfo subclasses,
                    // so try to pick out the real type.
                    val parent = node.uastParent
                    if (parent is UQualifiedReferenceExpression) {
                        val receiver = parent.receiver
                        val specificOwner = receiver.getExpressionType()?.canonicalText
                            ?: (receiver as? UReferenceExpression)?.getQualifiedName()
                        val specificApi = if (specificOwner != null)
                            apiDatabase.getFieldVersion(specificOwner, name)
                        else
                            -1
                        if (specificApi != -1 && specificOwner != null) {
                            if (specificApi < api) {
                                // Make sure the error message reflects the correct (lower)
                                // minSdkVersion if we have a more specific match on the field
                                // type
                                api = specificApi
                                owner = specificOwner
                            }
                            if (specificApi > minSdk && specificApi > getTargetApi(node)) {
                                if (isSuppressed(context, specificApi, node, minSdk)) {
                                    return
                                }
                            } else {
                                return
                            }
                        } else {
                            if ((specificApi == 28 || specificApi == 29) && specificOwner == "android.app.TaskInfo") {
                                return
                            }
                        }
                    }

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
                    val fqcn = getFqcn(owner) + '#'.toString() + name
                    report(
                        issue,
                        node,
                        location,
                        "Field",
                        fqcn,
                        api,
                        minSdk,
                        apiLevelFix(api),
                        owner,
                        name
                    )
                }
            }
        }
    }

    private fun getApiLevelString(requires: Int): String {
        // For preview releases, don't show the API level as a number; show it using
        // a version code
        return if (requires <= SdkVersionInfo.HIGHEST_KNOWN_STABLE_API) {
            requires.toString()
        } else {
            SdkVersionInfo.getCodeName(requires) ?: requires.toString()
        }
    }

    private fun checkObsoleteSdkVersion(context: JavaContext, node: UElement) {
        val binary = node.getParentOfType(UBinaryExpression::class.java, true)
        if (binary != null) {
            val minSdk = getMinSdk(context)
            // Note that we do NOT use the app's minSdkVersion here; the library's
            // minSdkVersion should increased instead since it's possible that
            // this library is used elsewhere with a lower minSdkVersion than the
            // main min sdk, and deleting these calls would cause crashes in
            // that usage.
            val constraint = getVersionCheckConditional(binary)
            if (constraint != null) {
                val always = constraint.alwaysAtLeast(minSdk)
                val never = constraint.neverAtMost(minSdk)
                val message =
                    when {
                        always -> "Unnecessary; SDK_INT is always >= $minSdk"
                        never -> "Unnecessary; SDK_INT is never < $minSdk"
                        else -> return
                    }
                context.report(
                    Incident(
                        OBSOLETE_SDK,
                        message,
                        context.getLocation(binary),
                        binary,
                        LintFix.create().data(KEY_CONDITIONAL, always)
                    )
                )
            }
        }
    }

    override fun checkFolder(context: ResourceContext, folderName: String) {
        val folderVersion = context.folderVersion
        val minSdkVersion = context.project.minSdkVersion
        if (folderVersion > 1 && folderVersion <= minSdkVersion.featureLevel) {
            // Same comment as checkObsoleteSdkVersion: We limit this check
            // to the library's minSdkVersion, not the app minSdkVersion,
            // since encouraging to combine these resources can lead to
            // crashes
            val folderConfig =
                FolderConfiguration.getConfigForFolder(folderName) ?: error(context.file)
            folderConfig.versionQualifier = null
            val resourceFolderType = context.resourceFolderType ?: error(context.file)
            val newFolderName = folderConfig.getFolderName(resourceFolderType)
            val message = "This folder configuration (`v$folderVersion`) is unnecessary; " +
                "`minSdkVersion` is ${minSdkVersion.apiString}. " +
                "Merge all the resources in this folder " +
                "into `$newFolderName`."
            context.report(
                Incident(
                    OBSOLETE_SDK,
                    message,
                    Location.create(context.file),
                    fix().data(
                        KEY_FILE, context.file,
                        KEY_FOLDER_NAME, newFolderName,
                        KEY_REQUIRES_API, minSdkVersion.apiLevel
                    )
                )
            )
        }
    }

    companion object {
        @JvmField
        val REQUIRES_API_ANNOTATION = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "RequiresApi")

        const val KEY_FILE = "file"
        const val KEY_REQUIRES_API = "requiresApi"
        const val KEY_FOLDER_NAME = "folderName"
        const val KEY_CONDITIONAL = "conditional"
        const val KEY_REQUIRE_CLASS = "requireClass"
        private const val KEY_MESSAGE = "message"
        private const val KEY_MIN_API = "minSdk"
        private const val KEY_OWNER = "owner"
        private const val KEY_NAME = "name"
        private const val KEY_DESUGAR = "desugar"

        private const val SDK_SUPPRESS_ANNOTATION = "android.support.test.filters.SdkSuppress"
        private const val ANDROIDX_SDK_SUPPRESS_ANNOTATION = "androidx.test.filters.SdkSuppress"
        private const val ATTR_PROPERTY_VALUES_HOLDER = "propertyValuesHolder"

        private val JAVA_IMPLEMENTATION = Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE)

        /** Accessing an unsupported API. */
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

        /** Accessing an inlined API on older platforms. */
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
            implementation = JAVA_IMPLEMENTATION
        )

        /** Method conflicts with new inherited method. */
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
            implementation = JAVA_IMPLEMENTATION
        )

        /** Attribute unused on older versions. */
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

        /** Obsolete SDK_INT version check. */
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
            implementation = JAVA_IMPLEMENTATION
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
            return LintFix.create().data(KEY_REQUIRES_API, api)
        }

        private fun isTargetAnnotation(fqcn: String): Boolean {
            return fqcn == FQCN_TARGET_API ||
                isRequiresApiAnnotation(fqcn) ||
                fqcn == SDK_SUPPRESS_ANNOTATION ||
                fqcn == ANDROIDX_SDK_SUPPRESS_ANNOTATION ||
                fqcn == TARGET_API // with missing imports
        }

        private fun isRequiresApiAnnotation(fqcn: String): Boolean {
            return REQUIRES_API_ANNOTATION.isEquals(fqcn) ||
                fqcn == "RequiresApi" // With missing imports
        }

        /**
         * Returns true if this attribute is in a drawable document with
         * one of the root tags that require API 21.
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
         * Is the given attribute a "benign" unused attribute, one we
         * probably don't need to flag to the user as not applicable
         * on all versions? These are typically attributes which add
         * some nice platform behavior when available, but that are
         * not critical and developers would not typically need to be
         * aware of to try to implement workarounds on older platforms.
         */
        fun isBenignUnusedAttribute(name: String): Boolean {
            return when (name) {
                ATTR_LABEL_FOR,
                ATTR_TEXT_IS_SELECTABLE,
                ATTR_FULL_BACKUP_CONTENT,
                ATTR_TEXT_ALIGNMENT,
                ATTR_ROUND_ICON,
                ATTR_IMPORTANT_FOR_AUTOFILL,
                ATTR_AUTOFILL_HINTS,
                "foregroundServiceType",
                "autofilledHighlight",
                "requestLegacyExternalStorage",

                // The following attributes are benign because aapt2 will rewrite them
                // into the safe alternatives; e.g. paddingHorizontal gets rewritten as
                // paddingLeft and paddingRight; this is done in aapt2's
                // ResourceFileFlattener::ResourceFileFlattener
                "paddingHorizontal",
                "paddingVertical",
                "layout_marginHorizontal",
                "layout_marginVertical" -> true

                else -> false
            }
        }

        private fun checkSimpleDateFormat(
            context: JavaContext,
            call: UCallExpression,
            minSdk: Int
        ) {
            if (minSdk >= 24) {
                // Already OK
                return
            }

            val expressions = call.valueArguments
            if (expressions.isEmpty()) {
                return
            }
            val argument = expressions[0]
            var warned: MutableList<Char>? = null
            var checked: Char = 0.toChar()
            val constant = when (argument) {
                is ULiteralExpression -> argument.value
                is UInjectionHost ->
                    argument.evaluateToString()
                        ?: ConstantEvaluator().allowUnknowns().evaluate(argument)
                        ?: return
                else -> ConstantEvaluator().allowUnknowns().evaluate(argument) ?: return
            }
            if (constant is String) {
                var isEscaped = false
                for (index in 0 until constant.length) {
                    when (val c = constant[index]) {
                        '\'' -> isEscaped = !isEscaped
                        // Gingerbread
                        'L', 'c',
                        // Nougat
                        'Y', 'X', 'u' -> {
                            if (!isEscaped && c != checked && (warned == null || !warned.contains(c))) {
                                val api = if (c == 'L' || c == 'c') 9 else 24
                                if (minSdk >= api) {
                                    checked = c
                                } else if (isWithinVersionCheckConditional(context, argument, api) ||
                                    isPrecededByVersionCheckExit(context, argument, api)
                                ) {
                                    checked = c
                                } else {
                                    var end = index + 1
                                    while (end < constant.length && constant[end] == c) {
                                        end++
                                    }

                                    val location = if (argument is ULiteralExpression) {
                                        context.getRangeLocation(argument, index, end - index)
                                    } else if (argument is UInjectionHost && argument is UPolyadicExpression &&
                                        argument.operator == UastBinaryOperator.PLUS &&
                                        argument.operands.size == 1 &&
                                        argument.operands.first() is ULiteralExpression
                                    ) {
                                        context.getRangeLocation(argument.operands[0], index, end - index)
                                    } else {
                                        context.getLocation(argument)
                                    }

                                    val incident = Incident(
                                        issue = UNSUPPORTED,
                                        scope = call,
                                        location = location,
                                        message = "", // always formatted in accept() before reporting
                                        fix = apiLevelFix(api)
                                    )
                                    val map = LintMap().apply {
                                        put(KEY_REQUIRES_API, api)
                                        put(KEY_MIN_API, minSdk)
                                        put(
                                            KEY_MESSAGE,
                                            "The pattern character '$c' requires API level $api (current min is %1\$d) : \"`$constant`\""
                                        )
                                    }
                                    context.report(incident, map)
                                    val list = warned ?: ArrayList<Char>().also { warned = it }
                                    list.add(c)
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Returns the minimum SDK to use in the given element context,
         * or -1 if no `tools:targetApi` attribute was found.
         *
         * @param element the element to look at, including parents
         * @return the API level to use for this element, or -1
         */
        private fun getLocalMinSdk(element: Element): Int {
            var current = element

            while (true) {
                val targetApi = current.getAttributeNS(TOOLS_URI, ATTR_TARGET_API)
                if (targetApi != null && targetApi.isNotEmpty()) {
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
         * Checks if the current project supports features added in
         * `minGradleVersion` version of the Android gradle plugin.
         *
         * @param context Current context.
         * @param minGradleVersionString Version in which support for a
         *     given feature was added, or null if
         *     it's not supported at build time.
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
         * Checks whether the given instruction is a benign usage of
         * a constant defined in a later version of Android than the
         * application's `minSdkVersion`.
         *
         * @param node the instruction to check
         * @param name the name of the constant
         * @param owner the field owner
         * @return true if the given usage is safe on older versions
         *     than the introduction level of the constant
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
                ) && (
                    name == "CHOICE_MODE_NONE" ||
                        name == "CHOICE_MODE_MULTIPLE" ||
                        name == "CHOICE_MODE_SINGLE"
                    )
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
                        if (node.isUastChildOf(condition, false)) {
                            return true
                        }
                    }
                    return false
                } else if (curr is UIfExpression) {
                    val condition = curr.condition
                    return node.isUastChildOf(condition, false)
                } else if (curr is UMethod || curr is UClass) {
                    break
                }
                curr = curr.uastParent
            }

            return false
        }

        /**
         * Returns the first (in DFS order) inheritance chain connecting
         * the two given classes.
         *
         * @param derivedClass the derived class
         * @param baseClass the base class
         * @return The first found inheritance chain connecting the two
         *     classes, or `null` if the classes are not
         *     related by inheritance. The `baseClass` is not
         *     included in the returned inheritance chain, which
         *     will be empty if the two classes are the same.
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
            return driver.isSuppressed(context, UNSUPPORTED, element) ||
                driver.isSuppressed(context, INLINED, element) ||
                isWithinVersionCheckConditional(context, element, api) ||
                isPrecededByVersionCheckExit(context, element, api)
        }

        @JvmOverloads
        @JvmStatic
        fun getTargetApi(
            scope: UElement?,
            isApiLevelAnnotation: (String) -> Boolean = ::isTargetAnnotation
        ): Int {
            var current = scope
            while (current != null) {
                if (current is UAnnotated) {
                    val targetApi = getTargetApiForAnnotated(current, isApiLevelAnnotation)
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
         * Returns the API level for the given AST node if specified
         * with an `@TargetApi` annotation.
         *
         * @param annotated the annotated element to check
         * @return the target API level, or -1 if not specified
         */
        private fun getTargetApiForAnnotated(
            annotated: UAnnotated?,
            isApiLevelAnnotation: (String) -> Boolean
        ): Int {
            if (annotated == null) {
                return -1
            }

            //noinspection AndroidLintExternalAnnotations
            for (annotation in annotated.uAnnotations) {
                val fqcn = annotation.qualifiedName
                if (fqcn != null && isApiLevelAnnotation(fqcn)) {
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
                    val start = text.indexOf('(')
                    if (start == -1) {
                        continue
                    }
                    val colon = text.indexOf(':') // skip over @file: etc
                    val annotationString =
                        text.substring(if (colon < start) colon + 1 else 0, start)
                    if (start != -1 && isApiLevelAnnotation(annotationString)) {
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
                            if (name.isNotEmpty()) {
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
