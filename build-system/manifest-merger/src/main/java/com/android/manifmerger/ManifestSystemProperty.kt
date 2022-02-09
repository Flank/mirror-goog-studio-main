/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.manifmerger

import com.android.SdkConstants
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import com.android.manifmerger.Actions.AttributeRecord
import com.android.manifmerger.Actions.NodeRecord
import com.android.manifmerger.ManifestMerger2.AutoAddingProperty
import com.android.manifmerger.ManifestModel.NodeTypes
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils
import org.w3c.dom.Element

/**
 * List of manifest files properties that can be directly overridden without using a
 * placeholder.
 */
enum class ManifestSystemProperty : AutoAddingProperty {
    /**
     * Allow setting the merged manifest file package name.
     */
    PACKAGE {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElement(this, actionRecorder, value, document.rootNode)
        }
    },

    /**
     * @see [
     * http://developer.android.com/guide/topics/manifest/manifest-element.html.vcode](http://developer.android.com/guide/topics/manifest/manifest-element.html.vcode)
     */
    VERSION_CODE {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(this, actionRecorder, value, document.rootNode)
        }
    },

    /**
     * @see [
     * http://developer.android.com/guide/topics/manifest/manifest-element.html.vname](http://developer.android.com/guide/topics/manifest/manifest-element.html.vname)
     */
    VERSION_NAME {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(this, actionRecorder, value, document.rootNode)
        }
    },

    /**
     * @see [
     * http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.min](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.min)
     */
    MIN_SDK_VERSION {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetUseSdk(actionRecorder, document)
            )
        }
    },

    /**
     * @see [
     * http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.target](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.target)
     */
    TARGET_SDK_VERSION {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetUseSdk(actionRecorder, document)
            )
        }
    },

    /**
     * @see [
     * http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.max](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.max)
     */
    MAX_SDK_VERSION {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetUseSdk(actionRecorder, document)
            )
        }
    },

    /**
     * Name of the instrumentation runner.
     *
     * @see [
     * http://developer.android.com/guide/topics/manifest/instrumentation-element.html](http://developer.android.com/guide/topics/manifest/instrumentation-element.html)
     */
    NAME {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetInstrumentation(actionRecorder, document)
            )
        }
    },

    /**
     * Target package for the instrumentation.
     *
     * @see [
     * http://developer.android.com/guide/topics/manifest/instrumentation-element.html](http://developer.android.com/guide/topics/manifest/instrumentation-element.html)
     */
    TARGET_PACKAGE {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetInstrumentation(actionRecorder, document)
            )
        }
    },

    /**
     * Functional test attribute for the instrumentation.
     *
     * @see [
     * http://developer.android.com/guide/topics/manifest/instrumentation-element.html](http://developer.android.com/guide/topics/manifest/instrumentation-element.html)
     */
    FUNCTIONAL_TEST {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetInstrumentation(actionRecorder, document)
            )
        }
    },

    /**
     * Handle profiling attribute for the instrumentation.
     *
     * @see [
     * http://developer.android.com/guide/topics/manifest/instrumentation-element.html](http://developer.android.com/guide/topics/manifest/instrumentation-element.html)
     */
    HANDLE_PROFILING {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetInstrumentation(actionRecorder, document)
            )
        }
    },

    /**
     * Label attribute for the instrumentation.
     *
     * @see [
     * http://developer.android.com/guide/topics/manifest/instrumentation-element.html](http://developer.android.com/guide/topics/manifest/instrumentation-element.html)
     */
    LABEL {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetInstrumentation(actionRecorder, document)
            )
        }
    },

    /**
     * Test_only attribute for the application.
     *
     * @see [
     * https://developer.android.com/guide/topics/manifest/application-element.testOnly](https://developer.android.com/guide/topics/manifest/application-element.testOnly)
     */
    TEST_ONLY {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            val msp = createOrGetElementInManifest(
                actionRecorder,
                document,
                NodeTypes.APPLICATION,
                "application injection requested"
            )
            addToElementInAndroidNS(this, actionRecorder, value, msp)
        }
    },

    /**
     * Shell attribute set for Profileable
     *
     * @see [
     * https://developer.android.com/guide/topics/manifest/profileable-element](https://developer.android.com/guide/topics/manifest/profileable-element)
     */
    SHELL {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            // Assume there is always an application element.
            val applicationElement = document.getByTypeAndKey(NodeTypes.APPLICATION, null)
            val profileable = createOrGetProfileable(
                actionRecorder, document, applicationElement.get().xml
            )
            addToElementInAndroidNS(
                this, actionRecorder, value, profileable
            )
        }
    },

    /**
     * Enabled attribute set for Profileable
     *
     * @see [
     * https://developer.android.com/guide/topics/manifest/profileable-element](https://developer.android.com/guide/topics/manifest/profileable-element)
     */
    ENABLED {
        override fun addTo(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            value: String
        ) {
            // Assume there is always an application element.
            val applicationElement = document.getByTypeAndKey(NodeTypes.APPLICATION, null)
            val profileable = createOrGetProfileable(
                actionRecorder, document, applicationElement.get().xml
            )
            addToElementInAndroidNS(
                this,
                actionRecorder,
                value,
                profileable
            )
        }
    };

    fun toCamelCase(): String {
        return SdkUtils.constantNameToCamelCase(name)
    }

    companion object {
        // utility method to add an attribute which name is derived from the enum name().
        private fun addToElement(
            manifestSystemProperty: ManifestSystemProperty,
            actionRecorder: ActionRecorder,
            value: String,
            to: XmlElement
        ) {
            to.xml.setAttribute(manifestSystemProperty.toCamelCase(), value)
            val xmlAttribute = XmlAttribute(
                to,
                to.xml.getAttributeNode(manifestSystemProperty.toCamelCase()), null
            )
            recordElementInjectionAction(actionRecorder, to, xmlAttribute)
        }

        // utility method to add an attribute in android namespace which local name is derived from
        // the enum name().
        private fun addToElementInAndroidNS(
            manifestSystemProperty: ManifestSystemProperty,
            actionRecorder: ActionRecorder,
            value: String,
            to: XmlElement
        ) {
            val toolsPrefix = XmlUtils.lookupNamespacePrefix(
                to.xml, SdkConstants.ANDROID_URI, SdkConstants.ANDROID_NS_NAME, true
            )
            to.xml.setAttributeNS(
                SdkConstants.ANDROID_URI,
                toolsPrefix + XmlUtils.NS_SEPARATOR + manifestSystemProperty.toCamelCase(),
                value
            )
            val attr = to.xml.getAttributeNodeNS(
                SdkConstants.ANDROID_URI,
                manifestSystemProperty.toCamelCase()
            )
            val xmlAttribute = XmlAttribute(to, attr, null)
            recordElementInjectionAction(actionRecorder, to, xmlAttribute)
        }

        private fun recordElementInjectionAction(
            actionRecorder: ActionRecorder,
            to: XmlElement,
            xmlAttribute: XmlAttribute
        ) {
            actionRecorder.recordNodeAction(to, Actions.ActionType.INJECTED)
            actionRecorder.recordAttributeAction(
                xmlAttribute, AttributeRecord(
                    Actions.ActionType.INJECTED,
                    SourceFilePosition(to.sourceFile, SourcePosition.UNKNOWN),
                    xmlAttribute.id,
                    null,  /* reason */
                    null /* attributeOperationType */
                )
            )
        }

        // utility method to create or get an existing use-sdk xml element under manifest.
        // this could be made more generic by adding more metadata to the enum but since there is
        // only one case so far, keep it simple.
        private fun createOrGetUseSdk(
            actionRecorder: ActionRecorder, document: XmlDocument
        ): XmlElement {
            return createOrGetElementInManifest(
                actionRecorder,
                document,
                NodeTypes.USES_SDK,
                "use-sdk injection requested"
            )
        }

        /** See above for details, similar like for uses-sdk tag */
        private fun createOrGetInstrumentation(
            actionRecorder: ActionRecorder, document: XmlDocument
        ): XmlElement {
            return createOrGetElementInManifest(
                actionRecorder,
                document,
                NodeTypes.INSTRUMENTATION,
                "instrumentation injection requested"
            )
        }

        private fun createOrGetProfileable(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            applicationElement: Element
        ): XmlElement {
            return createOrGetElement(
                actionRecorder,
                document,
                applicationElement,
                NodeTypes.PROFILEABLE,
                "profileable injection requested"
            )
        }

        private fun createOrGetElementInManifest(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            nodeType: NodeTypes,
            message: String
        ): XmlElement {
            val manifest = document.xml.documentElement
            return createOrGetElement(actionRecorder, document, manifest, nodeType, message)
        }

        private fun createOrGetElement(
            actionRecorder: ActionRecorder,
            document: XmlDocument,
            parentElement: Element,
            nodeType: NodeTypes,
            message: String
        ): XmlElement {
            val elementName = document.model.toXmlName(nodeType)
            var nodes = parentElement.getElementsByTagName(elementName)
            if (nodes.length == 0) {
                nodes = parentElement.getElementsByTagNameNS(SdkConstants.ANDROID_URI, elementName)
            }
            return if (nodes.length == 0) {
                // create it first.
                val node = parentElement.ownerDocument.createElement(elementName)
                parentElement.appendChild(node)
                val xmlElement = XmlElement(node, document)
                val nodeRecord = NodeRecord(
                    Actions.ActionType.INJECTED,
                    SourceFilePosition(
                        xmlElement.sourceFile,
                        SourcePosition.UNKNOWN
                    ),
                    xmlElement.id,
                    message,
                    NodeOperationType.STRICT
                )
                actionRecorder.recordNodeAction(xmlElement, nodeRecord)
                xmlElement
            } else {
                XmlElement((nodes.item(0) as Element), document)
            }
        }
    }
}
