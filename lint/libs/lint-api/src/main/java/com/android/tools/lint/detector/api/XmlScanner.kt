/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.lint.detector.api

import com.android.resources.ResourceFolderType
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Specialized interface for detectors that scan XML files  */
interface XmlScanner : FileScanner, XmlScannerConstants {

    /**
     * Returns the list of elements that this detector wants to analyze. If non
     * null, this detector will be called (specifically, the
     * [.visitElement] method) for each matching element in the document.
     *
     * @return a collection of elements, or null, or the special
     * [XmlScannerConstants.ALL] marker to indicate that every single
     * element should be analyzed.
     */
    fun getApplicableElements(): Collection<String>?

    /**
     * Visit the given element.
     * @param context information about the document being analyzed
     * @param element the element to examine
     */
    fun visitElement(context: XmlContext, element: Element)

    /**
     * Visit the given element after its children have been analyzed.
     * @param context information about the document being analyzed
     * @param element the element to examine
     */
    fun visitElementAfter(context: XmlContext, element: Element)

    /**
     * Returns the list of attributes that this detector wants to analyze. If non
     * null, this detector will be called (specifically, the
     * [.visitAttribute] method) for each matching attribute in the document.
     *
     * @return a collection of attributes, or null, or the special
     * [XmlScannerConstants.ALL] marker to indicate that every single
     * attribute should be analyzed.
     */
    fun getApplicableAttributes(): Collection<String>?

    /**
     * Visit the given attribute.
     * @param context information about the document being analyzed
     * @param attribute the attribute node to examine
     */
    fun visitAttribute(context: XmlContext, attribute: Attr)

    /**
     * Visit the given document. The detector is responsible for its own iteration
     * through the document.
     * @param context information about the document being analyzed
     * @param document the document to examine
     */
    fun visitDocument(context: XmlContext, document: Document)

    /**
     * Returns whether this detector applies to the given folder type. This
     * allows the detectors to be pruned from iteration, so for example when we
     * are analyzing a string value file we don't need to look up detectors
     * related to layout.
     *
     * @param folderType the folder type to be visited
     * @return true if this detector can apply to resources in folders of the
     * given type
     */
    fun appliesTo(folderType: ResourceFolderType): Boolean
}
