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

package com.android.builder.symbols;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * A parser used for finding all inline declaration of android resources in XML files.
 *
 * <p>Apart from placing files into sub-directories of the resource directory and declaring them in
 * XML files inside the {@code values} directory, {@code id} resources can also be lazily declared
 * in other XML files in non-values directories.
 *
 * <p>For example, inside {@code layout/main_activity.xml} we could have a line such as:
 *
 * <pre>
 *     {@code android:id="@+id/activity_main"}
 * </pre>
 *
 * <p>This construction is an example of a inline declaration of a resource with the type {@code id}
 * and name {@code activity_main}. Even though it is not declared inside a {@code values} directory,
 * it still needs to be parsed and processed into a Symbol:
 *
 * <table>
 *     <caption>Parsing result</caption>
 *     <tr><th>Java type  </th><th>Resource type  </th><th>Resource name    </th><th>ID</th></tr>
 *     <tr><td>int        </td><td>id             </td><td>activity_main    </td><td>0 </td></tr>
 * </table>
 *
 * <p>It is also worth noting that some resources can be declared with a prefix like {@code aapt:}
 * or {@code android:}. Following aapt's original behaviour, we strip the type names from those
 * prefixes. This behaviour is deprecated and might be the support for it might end in the near
 * future.
 */
public class ResourceExtraXmlParser {
    /**
     * Finds all constructions of type <@code '"@+id/name"'> in the given file.
     *
     * @param xmlDocument an xml file to parse
     * @param idProvider the provider for IDs to assign to the resources
     * @return the symbols for all resources in the file
     */
    @NonNull
    public static SymbolTable parse(@NonNull Document xmlDocument, @NonNull IdProvider idProvider) {
        Element root = xmlDocument.getDocumentElement();
        if (root == null) {
            throw new ResourceValuesXmlParseException("XML document does not have a root element.");
        }
        SymbolTable.Builder builder = SymbolTable.builder();
        parseChild(root, builder, idProvider);

        return builder.build();
    }

    /**
     * Parses an Element in search of lazy resource declarations and afterwards parses the Element's
     * children recursively.
     */
    private static void parseChild(
            @NonNull Element element,
            @NonNull SymbolTable.Builder builder,
            @NonNull IdProvider idProvider) {

        // Check if the node contains any lazy resource declarations.
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            checkForResources(((Attr) attr).getValue(), builder, idProvider);
        }

        // Parse all of the Element's children as well, in case they contain lazy declarations.
        Node current = element.getFirstChild();
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                parseChild((Element) current, builder, idProvider);
            }
            current = current.getNextSibling();
        }
    }

    /**
     * Checks whether a given text is a lazy declaration of type "@+id/name". If it is, changes it
     * into a new Symbol and adds it into the SymbolTable builder.
     */
    private static void checkForResources(
            @Nullable String text,
            @NonNull SymbolTable.Builder builder,
            @NonNull IdProvider idProvider) {
        if (text != null && text.startsWith(SdkConstants.NEW_ID_PREFIX)) {

            String name = text.substring(SdkConstants.NEW_ID_PREFIX.length(), text.length());
            Symbol newSymbol =
                    Symbol.createSymbol(
                            ResourceType.ID,
                            SymbolUtils.canonicalizeValueResourceName(name),
                            SymbolJavaType.INT,
                            idProvider.next(ResourceType.ID));
            if (!builder.contains(newSymbol)) {
                builder.add(newSymbol);
            }
        }
    }
}
