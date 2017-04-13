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


import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Parser that can load a {@link SymbolTable} from a resource XML file. Resource XML files contain
 * zero or multiple resources of the following types:
 *
 * <table>
 *     <caption>Types of resources</caption>
 *     <tr><th>Type         </th><th>XML Tag (*1)             </th><th>Symbol Type          </th><th>Java Type    </th></tr>
 *     <tr><td>Animation    </td><td>{@code anim}             </td><td>{@code anim}         </td><td>{@code int}  </td></tr>
 *     <tr><td>Animator     </td><td>{@code animator}         </td><td>{@code animator}     </td><td>{@code int}  </td></tr>
 *     <tr><td>Attribute    </td><td>{@code attr}             </td><td>{@code attr}         </td><td>{@code int}  </td></tr>
 *     <tr><td>Boolean      </td><td>{@code bool}             </td><td>{@code bool}         </td><td>{@code int}  </td></tr>
 *     <tr><td>Color        </td><td>{@code color}            </td><td>{@code color}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Dimension    </td><td>{@code dimen}            </td><td>{@code dimen}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Drawable     </td><td>{@code drawable}         </td><td>{@code drawable}     </td><td>{@code int}  </td></tr>
 *     <tr><td>Enumeration  </td><td>{@code enum}             </td><td>{@code id}           </td><td>{@code int}  </td></tr>
 *     <tr><td>Fraction     </td><td>{@code fraction}         </td><td>{@code fraction}     </td><td>{@code int}  </td></tr>
 *     <tr><td>ID           </td><td>{@code id}               </td><td>{@code id}           </td><td>{@code int}  </td></tr>
 *     <tr><td>Integer      </td><td>{@code integer}          </td><td>{@code integer}      </td><td>{@code int}  </td></tr>
 *     <tr><td>Integer Array</td><td>{@code integer-array}    </td><td>{@code array}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Menu         </td><td>{@code menu}             </td><td>{@code menu}         </td><td>{@code int}  </td></tr>
 *     <tr><td>MipMap       </td><td>{@code mipmap}           </td><td>{@code mipmap}       </td><td>{@code int}  </td></tr>
 *     <tr><td>Plural       </td><td>{@code plurals}          </td><td>{@code plurals}      </td><td>{@code int}  </td></tr>
 *     <tr><td>Raw          </td><td>{@code raw}              </td><td>{@code raw}          </td><td>{@code int}  </td></tr>
 *     <tr><td>String       </td><td>{@code string}           </td><td>{@code string}       </td><td>{@code int}  </td></tr>
 *     <tr><td>String Array </td><td>{@code string-array}     </td><td>{@code array}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Style        </td><td>{@code style}            </td><td>{@code style}        </td><td>{@code int}  </td></tr>
 *     <tr><td>Styleable    </td><td>{@code declare-styleable}</td><td>{@code styleable}(*2)</td><td>{@code int[]}</td></tr>
 *     <tr><td>Transition   </td><td>{@code transition}       </td><td>{@code transition}   </td><td>{@code int}  </td></tr>
 *     <tr><td>Typed Array  </td><td>{@code array}            </td><td>{@code array}        </td><td>{@code int}  </td></tr>
 *     <tr><td>XML          </td><td>{@code xml}              </td><td>{@code xml}          </td><td>{@code int}  </td></tr>
 * </table>
 *
 * <p>(*1) Resources can be also declared in an extended form where the XML Tag is {@code "item"}
 * and the attribute {@code "type"} specifies whether the resource is an {@code "attr"}, a {@code
 * string} et cetera. Therefore a construction like this:
 *
 * <pre>
 * <resources>
 *     <declare-styleable name="PieChart">
 *         <attr name="showText" format="boolean" />
 *         <attr name="labelPosition" format="enum">
 *             <enum name="left" value="0"/>
 *             <enum name="right" value="1"/>
 *         </attr>
 *     </declare-styleable>
 * </resources>
 * </pre>
 *
 * <p>Is equal to the following construction that uses {@code "item"} tag:
 *
 * <pre>
 * <resources>
 *     <item type="declare-styleable" name="PieChart">
 *         <item type="attr" name="showText" format="boolean" />
 *         <item type="attr" name="labelPosition" format="enum">
 *             <item type="enum" name="left" value="0"/>
 *             <item type="enum" name="right" value="1"/>
 *         </item>
 *     </item>
 * </resources>
 * </pre>
 *
 * <p>It is also worth noting that some resources can be declared with a prefix like {@code aapt:}
 * or {@code android:}. Following aapt's original behaviour, we strip the type names from those
 * prefixes. This behaviour is deprecated and might be the support for it might end in the near
 * future.
 *
 * <p>(*2)The mapping of {@code declare-styleable} to symbols is complex. For each styleable, a
 * symbol of resource type {@code styleabe} is created of java type {@code int[]}. For each
 * attribute ({@code attr}) in the {@code declare-styleable} a symbol of resource type {@code
 * styleable} with java type {@code int} is created as well as a symbol of resource type {@code
 * attr} with hava type {@code int}. In case of the symbol with {@code styleable} type, its name is
 * the symbol name of the {@code declare-styleable} element joined with the name of the {@code attr}
 * element by an underscore character. The value of the int array in the {@code declare-styleable}
 * contains the IDs of all {@code styleable} symbols. So, for example, the following XML:
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
 *     class attr {
 *         public static int def = 2;
 *         public static int ghi = 4;
 *     }
 *     class styleable {
 *         public static int[] abc = { 1, 3 };
 *         public static int abc_def = 1;
 *         public static int abc_ghi = 3;
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

        Node current = root.getFirstChild();
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                parseChild((Element) current, builder, idProvider);
            }
            current = current.getNextSibling();
        }
        return builder.build();
    }

    /**
     * Parses a single child element from the main XML document.
     *
     * @param child the element to be parsed
     * @param builder the builder for the SymbolTable
     * @param idProvider the provider for IDs to assign to the resources
     */
    private static void parseChild(
            @NonNull Element child,
            @NonNull SymbolTable.Builder builder,
            @NonNull IdProvider idProvider) {

        String name = SymbolUtils.canonicalizeValueResourceName(getMandatoryAttr(child, "name"));
        String type = child.getTagName();
        if (type.equals(SdkConstants.TAG_ITEM)) {
            type = child.getAttribute(SdkConstants.ATTR_TYPE);
        }

        // Strip the type name of prefixes.
        if (type.contains(":")) {
            type = type.substring(type.lastIndexOf(':') + 1, type.length());
        }

        ResourceType resourceType = ResourceType.getEnum(type);

        if (resourceType == null) {
            throw new ResourceValuesXmlParseException(
                    "Unknown resource value XML element '" + type + "'");
        }

        switch (resourceType) {
            case ANIM:
            case ANIMATOR:
            case ARRAY:
            case BOOL:
            case COLOR:
            case DIMEN:
            case DRAWABLE:
            case FONT:
            case FRACTION:
            case ID:
            case INTEGER:
            case INTERPOLATOR:
            case LAYOUT:
            case MENU:
            case MIPMAP:
            case PLURALS:
            case RAW:
            case STRING:
            case STYLE:
            case TRANSITION:
            case XML:
                builder.add(
                        Symbol.createSymbol(
                                resourceType,
                                name,
                                SymbolJavaType.INT,
                                Integer.toString(idProvider.next())));
                break;
            case DECLARE_STYLEABLE:
                // We also need to find all the attributes declared under declare styleable.
                parseDeclareStyleable(child, idProvider, name, builder);
                break;
            case ATTR:
                // We also need to find all the enums declared under attr (if there are any).
                parseAttr(child, idProvider, name, builder);
                break;
            case PUBLIC:
                // Doesn't declare a resource.
                break;
            default:
                throw new ResourceValuesXmlParseException(
                        "Unknown resource value XML element '" + type + "'");
        }
    }

    /**
     * Parses a declare styleable element and finds all it's {@code attr} children to create new
     * Symbols for each them: a {@code styleable} Symbol with the name which is a concatenation of
     * the declare styleable's name, an underscore and the child's name; and a {@code attr} Symbol
     * with the name equal to the child's name.
     *
     * @param declareStyleable the declare styleable element we are parsing
     * @param idProvider the provider for IDs to assign to the resources
     * @param name name of the declare styleable element
     * @param builder the builder for the SymbolTable
     * @throws ResourceValuesXmlParseException if there is an illegal type under declare-styleable
     */
    private static void parseDeclareStyleable(
            @NonNull Element declareStyleable,
            @NonNull IdProvider idProvider,
            @NonNull String name,
            @NonNull SymbolTable.Builder builder) {
        List<String> attrValues = new ArrayList<>();

        Node attrNode = declareStyleable.getFirstChild();
        while (attrNode != null) {
            if (attrNode.getNodeType() != Node.ELEMENT_NODE) {
                attrNode = attrNode.getNextSibling();
                continue;
            }

            Element attrElement = (Element) attrNode;
            String tagName = attrElement.getTagName();
            if (tagName.equals(SdkConstants.TAG_ITEM)) {
                tagName = attrElement.getAttribute(SdkConstants.ATTR_TYPE);
            }

            if (!tagName.equals(ResourceType.ATTR.getName())
                    || attrElement.getNamespaceURI() != null) {
                throw new ResourceValuesXmlParseException(
                        String.format(
                                "Illegal type under declare-styleable:"
                                        + " was <%s>, only accepted is <attr>",
                                tagName));
            }

            String attrName =
                    SymbolUtils.canonicalizeValueResourceName(
                            getMandatoryAttr(attrElement, "name"));

            parseAttr(attrElement, idProvider, attrName, builder);

            String attrValue = Integer.toString(idProvider.next());

            Symbol newStyleable =
                    Symbol.createSymbol(
                            ResourceType.STYLEABLE,
                            name + "_" + attrName,
                            SymbolJavaType.INT,
                            attrValue);

            builder.add(newStyleable);
            attrValues.add(attrValue);

            attrNode = attrNode.getNextSibling();
        }
        builder.add(
                Symbol.createSymbol(
                        ResourceType.STYLEABLE,
                        name,
                        SymbolJavaType.INT_LIST,
                        "{" + Joiner.on(',').join(attrValues) + "}"));
    }

    /**
     * Parses an attribute element and finds all it's {@code enum} children to create new Symbols
     * for each them: an {@code id} Symbol with the name equal to the child's name.
     *
     * @param attr the declare styleable element we are parsing
     * @param idProvider the provider for IDs to assign to the resources
     * @param name name of the attr element
     * @param builder the builder for the SymbolTable
     * @throws ResourceValuesXmlParseException if there is an illegal type under attr
     */
    private static void parseAttr(
            @NonNull Element attr,
            @NonNull IdProvider idProvider,
            @NonNull String name,
            @NonNull SymbolTable.Builder builder) {

        Node enumNode = attr.getFirstChild();
        while (enumNode != null) {
            if (enumNode.getNodeType() != Node.ELEMENT_NODE) {
                enumNode = enumNode.getNextSibling();
                continue;
            }

            Element enumElement = (Element) enumNode;
            String tagName = enumElement.getTagName();
            if (tagName.equals(SdkConstants.TAG_ITEM)) {
                tagName = enumElement.getAttribute(SdkConstants.ATTR_TYPE);
            }

            if (!tagName.equals(SdkConstants.TAG_ENUM) || enumElement.getNamespaceURI() != null) {
                // We only care about enums. If there is a different tag (e.g. "flag") we ignore it.
                enumNode = enumNode.getNextSibling();
                continue;
            }

            Symbol newEnum =
                    Symbol.createSymbol(
                            ResourceType.ID,
                            SymbolUtils.canonicalizeValueResourceName(
                                    getMandatoryAttr(enumElement, "name")),
                            SymbolJavaType.INT,
                            Integer.toString(idProvider.next()));

            if (!builder.contains(newEnum)) {
                builder.add(newEnum);
            }
            enumNode = enumNode.getNextSibling();
        }

        Symbol newAttr =
                Symbol.createSymbol(
                        ResourceType.ATTR,
                        name,
                        SymbolJavaType.INT,
                        Integer.toString(idProvider.next()));

        if (!builder.contains(newAttr)) {
            builder.add(newAttr);
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
