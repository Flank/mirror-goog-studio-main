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

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.VALUE_TRUE
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.annotations.VisibleForTesting
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier.BCP_47_PREFIX
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getBaseName
import com.android.utils.XmlUtils.getPreviousTagByName
import com.android.utils.iterator
import com.android.utils.next
import com.android.utils.subtag
import com.google.common.collect.Sets
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Check which makes sure that an actions.xml file is correct.
 * See XmltreeActionsSchemaParser in the dev tools codebase to
 * check behavior enforced in the dev console.
 *
 * TODO: Check that URLs declared in <fulfillment> tags have associated <intent-filter> entries
 *   declared on at least one <activity> in the Android Manifest
 */
class ActionsXmlDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (TAG_ACTIONS != root.tagName) {
            if (TAG_ACTION == root.tagName) {
                wrongParent(context, root, TAG_ACTIONS)
            }
            return
        }

        checkActions(context, document.documentElement ?: return)

        ensureRegisteredInManifest(context, root)
    }

    private fun ensureRegisteredInManifest(context: XmlContext, actions: Element) {
        // Make sure this actions resource is registered in the manifest
        if (context.project.isLibrary) {
            return
        }

        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return
        val application = root.subtag(TAG_APPLICATION) ?: return
        val actionResourceName = getBaseName(context.file.name)
        var metadata = application.subtag(TAG_META_DATA)
        while (metadata != null) {
            val name = metadata.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "com.google.android.actions") {
                val resource = metadata.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
                val url = ResourceUrl.parse(resource)
                if (url != null && url.name == actionResourceName) {
                    // Found the resource declaration
                    return
                }
            }
            metadata = metadata.next(TAG_META_DATA)
        }

        context.report(
            ISSUE, actions, context.getElementLocation(actions),
            "This action resource should be registered in the manifest under the " +
                    "`<application>` tag as " +
                    "`<meta-data android:name=\"com.google.android.actions\" " +
                    "android:resource=\"@xml/$actionResourceName\" />`"
        )
    }

    private fun checkActions(context: XmlContext, actions: Element) {
        for (action in actions) {
            checkParent(context, action) ?: return
            val tag = action.tagName
            if (tag == TAG_ACTION) {
                checkAction(context, action)
            } // else, like dev console, ignore unrecognized elements.
        }

        // Make sure the locales listed in the locale string are valid locales
        val localeNode = actions.getAttributeNodeNS(ACTIONS_URI, "supportedLocales")
        if (localeNode != null) {
            val locales = localeNode.value
            var index = 0
            locales.split(delimiters = ",").forEach { locale ->
                val bcp = BCP_47_PREFIX + locale.trim().replace("-", "+")
                LocaleQualifier.getQualifier(bcp) ?: run {
                    val loc = context.getValueLocation(localeNode)
                    var start = loc.start ?: return
                    // Adjust offset to attribute value
                    start = DefaultPosition(start.line, start.column + index, start.offset + index)
                    val end = DefaultPosition(
                        start.line,
                        start.column + locale.length,
                        start.offset + locale.length
                    )
                    val location = Location.Companion.create(context.file, start, end)
                    context.report(
                        ISSUE, actions, location,
                        "Invalid BCP-47 locale qualifier `$locale`"
                    )
                    return
                }
                index += locale.length + 1
            }
        }
    }

    private fun checkAction(context: XmlContext, action: Element) {
        checkParent(context, action) ?: return
        // Make sure name is defined
        checkRequiredAttribute(context, action, ACTIONS_URI, ATTR_INTENT_NAME) ?: return

        var atLeastOneFulfillment = false
        var parameterNames: MutableSet<String>? = null
        var foundNonRequiredTemplate = false
        for (child in action) {
            val tag = child.tagName
            if (tag == TAG_ACTION) {
                nestingNotAllowed(context, child)
                return
            }
            checkParent(context, child) ?: return
            when (tag) {
                TAG_PARAMETER -> {
                    if (parameterNames == null) {
                        parameterNames = mutableSetOf()
                    }
                    checkParameter(context, child, parameterNames)
                }
                TAG_FULFILLMENT -> {
                    checkFulfillment(context, child)
                    atLeastOneFulfillment = true
                    if (!templateRequiresParameters(child)) {
                        foundNonRequiredTemplate = true
                    }
                }
                else -> {
                    // Like dev console, ignore unrecognized elements.
                }
            }
        }

        // Make sure we have at least one fulfillment
        if (!atLeastOneFulfillment) {
            context.report(
                ISSUE, action, context.getElementLocation(action),
                "`<action>` must declare a `<fulfillment>`"
            )
        } else if (!foundNonRequiredTemplate) {
            context.report(
                ISSUE, action, context.getElementLocation(action),
                "At least one <fulfillment> `$ATTR_URL_TEMPLATE` must not be required"
            )
        }
    }

    /** Returns whether the template on this template requires parameters; this
     * is true if the template has one or more required parameters */
    private fun templateRequiresParameters(fulfillment: Element): Boolean {
        for (child in fulfillment) {
            if (child.tagName == TAG_PARAMETER_MAPPING) {
                val required = child.getAttributeNS(ACTIONS_URI, ATTR_REQUIRED)
                if (required == VALUE_TRUE) {
                    return true
                }
            }
        }

        return false
    }

    private fun checkParameter(
        context: XmlContext,
        parameter: Element,
        parameterNames: MutableSet<String>
    ) {
        checkParent(context, parameter) ?: return

        // Make sure name is defined
        checkRequiredAttribute(context, parameter, ACTIONS_URI, ATTR_NAME) ?: return
        // Make sure type is defined
        checkRequiredAttribute(context, parameter, ACTIONS_URI, ATTR_TYPE)

        // Make sure name is unique
        val name = checkRequiredAttribute(context, parameter, ACTIONS_URI, ATTR_NAME) ?: return
        checkNotAlreadyPresent(
            name,
            ATTR_NAME,
            parameterNames,
            context,
            parameter,
            TAG_ACTION,
            TAG_PARAMETER
        )

        for (child in parameter) {
            val tag = child.tagName
            if (tag == TAG_PARAMETER) {
                nestingNotAllowed(context, child)
                return
            }
            checkParent(context, child) ?: return
            if (tag == TAG_ENTITY_SET_REFERENCE) {
                checkEntitySetReference(context, child)
            }
        }
    }

    private fun checkEntitySetReference(context: XmlContext, entitySetReference: Element) {
        checkRequiredAttribute(
            context,
            entitySetReference,
            ACTIONS_URI,
            ATTR_URL_FILTER
        ) ?: return
    }

    private fun checkFulfillment(context: XmlContext, fulfillment: Element) {
        checkParent(context, fulfillment) ?: return
        val urlTemplate = checkRequiredAttribute(
            context,
            fulfillment,
            ACTIONS_URI,
            ATTR_URL_TEMPLATE,
            allowReference = false
        ) ?: return

        val parameters = getUriTemplateParameters(urlTemplate)

        val intentParameterNames = mutableSetOf<String>()
        val urlParameters = mutableSetOf<String>()
        for (child in fulfillment) {
            val tag = child.tagName
            if (tag == TAG_FULFILLMENT) {
                nestingNotAllowed(context, child)
                return
            }
            checkParent(context, child) ?: return
            when (tag) {
                TAG_PARAMETER_MAPPING -> {
                    val parameter =
                        checkParameterMapping(context, child, intentParameterNames, urlParameters)
                    if (parameter != null && !parameters.contains(parameter) &&
                        !parameter.startsWith(PREFIX_RESOURCE_REF)
                    ) {
                        context.report(
                            ISSUE, child, context.getElementLocation(child),
                            "The parameter `$parameter` is not present in the `$ATTR_URL_TEMPLATE`"
                        )
                    }
                }
                else -> {
                    // Like dev console, ignore unrecognized elements.
                }
            }
        }

        // Make sure the parameters are fully mapped
        val missing = Sets.difference(parameters, urlParameters)
        if (missing.isNotEmpty()) {
            val attributeLocation = context.getValueLocation(
                fulfillment.getAttributeNodeNS(ACTIONS_URI, ATTR_URL_TEMPLATE)
            )
            val message =
                if (missing.size == 1) {
                    "The parameter ${missing.first()} is not defined as a `<$TAG_PARAMETER_MAPPING>` element below"
                } else {
                    "The parameters ${missing.joinToString(separator = " and ") { it }
                    } are not defined as `<$TAG_PARAMETER_MAPPING>` elements below"
                }
            context.report(
                ISSUE, fulfillment, attributeLocation,
                message
            )
        }
    }

    /**
     * Checks parameter mapping parameter and returns the url parameter name (or null
     * if not declared or any other validation error was found)
     */
    private fun checkParameterMapping(
        context: XmlContext,
        parameterMapping: Element,
        intentParams: MutableSet<String>,
        urlParameters: MutableSet<String>
    ): String? {
        val intentParameter = checkRequiredAttribute(
            context, parameterMapping, ACTIONS_URI, ATTR_INTENT_PARAMETER, allowReference = false
        ) ?: return null

        checkParent(context, parameterMapping) ?: return null

        val urlParameter = checkRequiredAttribute(
            context,
            parameterMapping,
            ACTIONS_URI,
            ATTR_URL_PARAMETER,
            allowReference = false
        ) ?: return null
        urlParameters.add(urlParameter)

        checkNotAlreadyPresent(
            intentParameter,
            ATTR_INTENT_PARAMETER,
            intentParams,
            context,
            parameterMapping,
            TAG_FULFILLMENT,
            TAG_PARAMETER_MAPPING
        ) ?: return null

        return urlParameter
    }

    companion object {
        /** Validation of `<actions>` XML elements */
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidActionsXml",
            briefDescription = "Invalid Action Descriptor",
            explanation = "Ensures that an actions XML file is properly formed",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = Implementation(
                ActionsXmlDetector::class.java, Scope.RESOURCE_FILE_SCOPE
            )
        )

        // Namespace for actions is in flux
        private const val ACTIONS_URI = AUTO_URI

        private const val TAG_ACTIONS = "actions"
        private const val TAG_ACTION = "action"
        private const val TAG_ACTION_DISPLAY = "action-display"
        private const val TAG_PARAMETER = "parameter"
        private const val TAG_FULFILLMENT = "fulfillment"
        private const val TAG_PARAMETER_MAPPING = "parameter-mapping"
        private const val TAG_ENTITY_SET_REFERENCE = "entity-set-reference"
        private const val ATTR_URL_TEMPLATE = "urlTemplate"
        private const val ATTR_URL_FILTER = "urlFilter"
        private const val ATTR_URL_PARAMETER = "urlParameter"
        private const val ATTR_INTENT_PARAMETER = "intentParameter"
        private const val ATTR_INTENT_NAME = "intentName"
        private const val ATTR_RESOURCE = "resource"

        /**
         * Checks that this element has the expected parent; this catches
         * cases where you cut & paste fragments into the wrong place.
         *
         * Returns null if a problem was found, otherwise true. Using null
         * here allows callers to use Kotlin's elvis operator to quickly bail
         * if an error was found (since we don't want to add multiple errors
         * once one has been found.
         */
        private fun checkParent(context: XmlContext, element: Element): Boolean? {
            val tag = element.tagName
            val expectedParent = when (tag) {
                TAG_ACTION -> TAG_ACTIONS
                TAG_ACTION_DISPLAY -> TAG_ACTION
                TAG_PARAMETER, TAG_FULFILLMENT -> TAG_ACTION
                TAG_PARAMETER_MAPPING -> TAG_FULFILLMENT
                TAG_ENTITY_SET_REFERENCE -> TAG_PARAMETER
                else -> return true
            }
            val actualParent = element.parentNode?.nodeName ?: return true

            if (expectedParent != actualParent) {
                wrongParent(context, element, expectedParent)
                return null
            } else if (tag == actualParent) {
                nestingNotAllowed(context, element)
                return null
            }

            return true
        }

        private fun wrongParent(context: XmlContext, element: Element, expected: String) {
            context.report(
                ISSUE, element, context.getNameLocation(element),
                "`<${element.tagName}>` must be inside `<$expected>`"
            )
        }

        private fun nestingNotAllowed(context: XmlContext, element: Element) {
            context.report(
                ISSUE, element, context.getNameLocation(element),
                "Nesting `<${element.tagName}>` is not allowed"
            )
        }

        /**
         * Checks that the given name is not already in the given names set;
         * if so reports a duplicate error. Returns null or true for the same
         * reason as documented in [checkParent].
         */
        private fun checkNotAlreadyPresent(
            name: String?,
            nameAttribute: String,
            parameterNames: MutableSet<String>,
            context: XmlContext,
            parameter: Element,
            parentTag: String,
            nameTag: String
        ): Boolean? {
            name ?: return true

            val duplicate = !parameterNames.add(name)
            if (duplicate) {
                val location = context.getLocation(
                    parameter.getAttributeNodeNS(ACTIONS_URI, nameAttribute)
                )

                var prev = getPreviousTagByName(parameter, parameter.tagName)
                while (prev != null) {
                    val attr = prev.getAttributeNodeNS(ACTIONS_URI, nameAttribute)
                    if (attr?.value == name) {
                        location.secondary = context.getLocation(attr)
                        break
                    }
                    prev = getPreviousTagByName(prev, parameter.tagName)
                }

                context.report(
                    ISSUE, parameter, location,
                    "`<$parentTag>` contains two `<$nameTag>` elements with the same $nameAttribute, `$name`"
                )
                return null
            }
            return true
        }

        /**
         * Checks that the given attribute is present on the element. Optionally
         * allows allows to allow/disallow blank values, and resource references.
         * Returns null or the actual resource value found for the same
         * reason as documented in [checkParent].
         */
        private fun checkRequiredAttribute(
            context: XmlContext,
            element: Element,
            namespace: String,
            attribute: String,
            allowBlank: Boolean = false,
            allowReference: Boolean = true
        ): String? {
            val value = element.getAttributeNS(namespace, attribute)
            if (value != null && (allowBlank || !value.isBlank())) {
                if (!allowReference && value.startsWith(PREFIX_RESOURCE_REF)) {
                    context.report(
                        ISSUE, element, context.getLocation(
                            element.getAttributeNodeNS(
                                namespace, attribute
                            )
                        ),
                        "`$attribute` must be a value, not a reference"
                    )
                }

                return value
            }
            var fullAttribute = attribute
            var prefix: String? = element.ownerDocument.lookupNamespaceURI(namespace)
            if (prefix == null) {
                val root = element.ownerDocument.documentElement
                val attributes = root.attributes
                var i = 0
                val n = attributes.length
                while (i < n) {
                    val a = attributes.item(i) as Attr
                    if (a.name.startsWith(XMLNS_PREFIX) && namespace == a.value) {
                        prefix = a.name.substring(XMLNS_PREFIX.length)
                        break
                    }
                    i++
                }
            }
            if (prefix != null) {
                fullAttribute = prefix + ':'.toString() + fullAttribute
            }
            val fix = LintFix.create().set().todo(namespace, attribute).build()
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `$fullAttribute`",
                fix
            )
            return null
        }

        // Lightweight parsing of https://tools.ietf.org/html/rfc6570 to
        // extract the parameter names for URL templates level 4.
        @VisibleForTesting
        fun getUriTemplateParameters(urlTemplate: String): Set<String> {
            val length = urlTemplate.length
            val variables = mutableSetOf<String>()
            for (begin in 0 until length) {
                val c = urlTemplate[begin]
                if (c == '{') {
                    val end = urlTemplate.indexOf('}', begin + 1)
                    if (end != -1) {
                        addVariables(variables, urlTemplate, begin, end + 1)
                    }
                }
            }

            return variables
        }

        /**
         * Adds in any variables found in the { } section of the URL template.
         * This is handling the range from [from] inclusive to [to] inclusive in string [s]
         * assuming the content is an "expression" according to RFC 6570.
         */
        private fun addVariables(variables: MutableSet<String>, s: String, from: Int, to: Int) {
            if (from > to - 2) { // empty
                return
            }

            // From https://tools.ietf.org/html/rfc6570#section-2.3
            //    expression    =  "{" [ operator ] variable-list "}"
            //    operator      =  op-level2 / op-level3 / op-reserve
            //    op-level2     =  "+" / "#"
            //    op-level3     =  "." / "/" / ";" / "?" / "&"
            //    op-reserve    =  "=" / "," / "!" / "@" / "|"
            val operator = s[from + 1]
            val hasOperator = when (operator) {
                '+', '#' -> true // op-level2
                '.', '/', ';', '?', '&' -> true // op-level3
                '=', ',', '!', '@', '|' -> true // op-reserve
                else -> false
            }
            var offset = from + if (hasOperator) 2 else 1
            while (offset < to) {
                // Find end of varspec
                val beginVarSpec = offset
                var endVarSpec = beginVarSpec
                var variableEnd = -1
                while (endVarSpec < to) {
                    //    variable-list =  varspec *( "," varspec )
                    //    varspec       =  varname [ modifier-level4 ]
                    //    varname       =  varchar *( ["."] varchar )
                    //    varchar       =  ALPHA / DIGIT / "_" / pct-encoded
                    //    modifier-level4 =  prefix / explode
                    //    prefix        =  ":" max-length
                    //    max-length    =  %x31-39 0*3DIGIT   ; positive integer < 10000
                    //    explode       =  "*"
                    val c = s[endVarSpec]
                    if (c == ',' || c == '}') {
                        if (variableEnd == -1) {
                            variableEnd = endVarSpec
                        }
                        break
                    } else if (c == ':' || c == '*') {
                        variableEnd = endVarSpec
                    }
                    endVarSpec++
                }

                if (variableEnd > beginVarSpec) {
                    val variable = s.substring(beginVarSpec, variableEnd)
                    variables.add(variable)
                }

                offset = endVarSpec + 1
            }
        }
    }
}
