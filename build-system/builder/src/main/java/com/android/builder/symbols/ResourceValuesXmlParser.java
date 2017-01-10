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

package com.android.builder.symbols;

import com.android.annotations.NonNull;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parser that can load a {@link SymbolTable} from a resource XML file. Resource XML files contain
 * zero or multiple resources of the following types:
 *
 * <table>
 *     <tr><th>Type         </th><th>XML Tag                  </th><th>Resource Type        </th><th>Java Type    </th></tr>
 *     <tr><td>Attribute    </td><td>{@code attr}             </td><td>{@code attr}         </td><td>{@code int}  </td></tr>
 *     <tr><td>Boolean      </td><td>{@code bool}             </td><td>{@code bool}         </td><td>{@code int}  </td></tr>
 *     <tr><td>Color        </td><td>{@code color}            </td><td>{@code color}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Dimension    </td><td>{@code dimen}            </td><td>{@code dimen}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Dimension    </td><td>{@code item}(*1)         </td><td>{@code dimen}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Drawable     </td><td>{@code drawable}         </td><td>{@code drawable}     </td><td>{@code int}  </td></tr>
 *     <tr><td>ID           </td><td>{@code item}(*2)         </td><td>{@code id}           </td><td>{@code int}  </td></tr>
 *     <tr><td>Integer      </td><td>{@code integer}          </td><td>{@code integer}      </td><td>{@code int}  </td></tr>
 *     <tr><td>Integer Array</td><td>{@code integer-array}    </td><td>{@code array}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Plural       </td><td>{@code plurals}          </td><td>{@code plurals}      </td><td>{@code int}  </td></tr>
 *     <tr><td>String       </td><td>{@code string}           </td><td>{@code string}       </td><td>{@code int}  </td></tr>
 *     <tr><td>String Array </td><td>{@code string-array}     </td><td>{@code array}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Style        </td><td>{@code style}            </td><td>{@code style}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Styleable    </td><td>{@code declare-styleable}</td><td>{@code styleable}(*3)</td><td>{@code int[]}</td></tr>
 *     <tr><td>Typed Array  </td><td>{@code array}            </td><td>{@code array}        </td><td>{@code int}  </td></tr>
 * </table>
 *
 * <p>(*1)Dimensions specified as {@code item} must have the attribute {@code type="dimen"}.
 *
 * <p>(*2)IDs have an {@code item} element with the attribute {@code type="id"}.
 *
 * <p>(*3)The mapping of {@code declare-styleable} to symbols is complex. For each styleable, a
 * symbol of resource type {@code styleabe} is created of java type {@code int[]}. For each
 * attribute ({@code attr}) in the {@code declare-styleable} a symbol of resource type
 * {@code styleable} with java type {@code int} is created. The symbol's name is the symbol name of
 * the {@code declare-styleable} element joined with the name of the {@code attr} element by an
 * underscore character. The value of the int array in the {@code declare-styleable} contains the
 * IDs of all {@code attr} elements. So, for example, the following XML:
 *
 * <pre>
 * <resources>
 *     <declare-styleable name="abc">
 *         <attr name="def" format="boolean"/>
 *         <attr name="ghi" format="int"/>
 *     </declare-styleable>
 * </resources>
 * </pre>
 *
 * <p>Will generate the following {@code R.java}:
 *
 * <pre>
 * class R {
 *     class styleable {
 *         public static int[] abc = { 1, 2 };
 *         public static int abc_def = 1;
 *         public static int abc_ghi = 2;
 *     }
 * }
 * </pre>
 */
public final class ResourceValuesXmlParser {

    private ResourceValuesXmlParser() {}


    /**
     * Constructs a {@link SymbolTable} from the given parsed XML document. The values for the
     * resource are drawn from the given provider. For testing purposes, this method guarantees that
     * IDs are assigned in the order the resources are provided in the XML document. However,
     * this guarantee is only to make testing simpler and non-test code should not rely on this
     * assumption as it may change, along with the required refactoring of the test code.
     *
     * @param xmlDocument the parsed XML document
     * @param idProvider the provider for IDs to assign to the resources
     * @return the symbols for all resources in the document
     */
    @NonNull
    public static SymbolTable parse(@NonNull Document xmlDocument, @NonNull IdProvider idProvider) {
        Element root = xmlDocument.getDocumentElement();
        if (root == null) {
            throw new ResourceValuesXmlParseException("XML document does not have a root element.");
        }

        if (!"resources".equals(root.getTagName())) {
            throw new ResourceValuesXmlParseException("XML document root is not 'resources'");
        }

        if (root.getNamespaceURI() != null) {
            throw new ResourceValuesXmlParseException("XML document root has a namespace");
        }

        SymbolTable.Builder builder = SymbolTable.builder();

        NodeList children = root.getChildNodes();
        int childrenCount = children.getLength();
        for (int i = 0; i < childrenCount; i++) {
            Node childNode = children.item(i);
            if (childNode instanceof Element) {
                Element child = (Element) childNode;

                parseChild(child, child.getTagName(), builder, idProvider);
            }
        }

        return builder.build();
    }

    /**
     * Parses a single child element from the main XML document.
     *
     * @param child the element to be parsed
     * @param type XML tag of the element or (in case of "item" tag) the resource type
     * @param builder the builder for the SymbolTable
     * @param idProvider the provider for IDs to assign to the resources
     */
    private static void parseChild(
            @NonNull Element child,
            @NonNull String type,
            @NonNull SymbolTable.Builder builder,
            @NonNull IdProvider idProvider) {

        String name = getMandatoryAttr(child, "name");

        switch (type) {
            case "array":
            case "integer-array":
            case "string-array":
                builder.add(
                        new Symbol(
                                "array",
                                name,
                                "int",
                                Integer.toString(idProvider.next())));
                break;
            case "attr":
            case "bool":
            case "color":
            case "dimen":
            case "drawable":
            case "integer":
            case "plurals":
            case "string":
            case "style":
            case "id":
                builder.add(
                        new Symbol(
                                type,
                                name,
                                "int",
                                Integer.toString(idProvider.next())));
                break;
            case "declare-styleable":
                List<String> attrValues = new ArrayList<>();

                NodeList attrList = child.getChildNodes();
                int attrListCount = attrList.getLength();
                for (int j = 0; j < attrListCount; j++) {
                    Node attrNode = attrList.item(j);
                    if (!(attrNode instanceof Element)) {
                        continue;
                    }

                    Element attrElement = (Element) attrNode;
                    if (!"attr".equals(attrElement.getTagName())
                            || attrElement.getNamespaceURI() != null) {
                        continue;
                    }

                    String attrName = getMandatoryAttr(attrElement, "name");
                    String attrValue = Integer.toString(idProvider.next());
                    builder.add(
                            new Symbol(
                                    "styleable",
                                    name + "_" + attrName,
                                    "int",
                                    attrValue));
                    attrValues.add(attrValue);
                }

                builder.add(
                        new Symbol(
                                "styleable",
                                name,
                                "int[]",
                                "{" + Joiner.on(',').join(attrValues) + "}"));
                break;
            case "item":
                parseChild(child, getMandatoryAttr(child, "type"), builder, idProvider);
                break;
            default:
                throw new ResourceValuesXmlParseException(
                        "Unknown resource value XML element '" + type + "'");
        }
    }

    /**
     * Obtains an attribute in an element that must exist.
     *
     * @param element the XML element
     * @param attrName the attribute name
     * @return the attribute value
     * @throws ResourceValuesXmlParseException the attribute does not exist
     */
    @NonNull
    private static String getMandatoryAttr(@NonNull Element element, @NonNull String attrName) {
        Attr attr = element.getAttributeNodeNS(null, attrName);
        if (attr == null) {
            throw new ResourceValuesXmlParseException(
                    "Element '"
                            + element.getTagName()
                            + "' should have attribute '"
                            + attrName
                            + "'");
        }

        return attr.getValue();
    }
}
