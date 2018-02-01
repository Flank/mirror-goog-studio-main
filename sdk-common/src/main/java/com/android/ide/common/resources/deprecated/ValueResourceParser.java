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

package com.android.ide.common.resources.deprecated;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX_LEN;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DeclareStyleableResourceValue;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.resources.ResourceType;
import com.google.common.base.Strings;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public final class ValueResourceParser extends DefaultHandler {

    private static final ResourceReference TMP_REF =
            new ResourceReference(ResourceType.STRING, "_tmp", false);

    public interface IValueResourceRepository {
        void addResourceValue(ResourceValue value);
    }

    private boolean inResources;
    private int mDepth;
    private ResourceValue mCurrentValue;
    private ArrayResourceValue mArrayResourceValue;
    private StyleResourceValue mCurrentStyle;
    private DeclareStyleableResourceValue mCurrentDeclareStyleable;
    private AttrResourceValue mCurrentAttr;
    private IValueResourceRepository mRepository;
    private final boolean mIsFramework;
    private final String mLibraryName;

    public ValueResourceParser(IValueResourceRepository repository, boolean isFramework, String libraryName) {
        mRepository = repository;
        mIsFramework = isFramework;
        mLibraryName = libraryName;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (mCurrentValue != null) {
            String value = mCurrentValue.getValue();
            value = value == null ? "" : ValueXmlHelper.unescapeResourceString(value, false, true);
            mCurrentValue.setValue(value);
        }

        if (inResources && qName.equals(TAG_RESOURCES)) {
            inResources = false;
        } else if (mDepth == 2) {
            mCurrentValue = null;
            mCurrentStyle = null;
            mCurrentDeclareStyleable = null;
            mCurrentAttr = null;
            mArrayResourceValue = null;
        } else if (mDepth == 3) {
            if (mArrayResourceValue != null && mCurrentValue != null) {
                mArrayResourceValue.addElement(mCurrentValue.getValue());
            }
            mCurrentValue = null;
            //noinspection VariableNotUsedInsideIf
            if (mCurrentDeclareStyleable != null) {
                mCurrentAttr = null;
            }
        }

        mDepth--;
        super.endElement(uri, localName, qName);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        try {
            mDepth++;
            if (!inResources && mDepth == 1) {
                if (qName.equals(TAG_RESOURCES)) {
                    inResources = true;
                }
            } else if (mDepth == 2 && inResources) {
                ResourceType type = getType(qName, attributes);

                if (type != null) {
                    // get the resource name
                    String name = attributes.getValue(ATTR_NAME);
                    if (name != null) {
                        ResourceReference newResource =
                                new ResourceReference(type, name, mIsFramework);
                        switch (type) {
                            case STYLE:
                                String parent = attributes.getValue(ATTR_PARENT);
                                mCurrentStyle =
                                        new StyleResourceValue(newResource, parent, mLibraryName);
                                mRepository.addResourceValue(mCurrentStyle);
                                break;
                            case DECLARE_STYLEABLE:
                                mCurrentDeclareStyleable =
                                        new DeclareStyleableResourceValue(
                                                newResource, null, mLibraryName);
                                mRepository.addResourceValue(mCurrentDeclareStyleable);
                                break;
                            case ATTR:
                                mCurrentAttr = new AttrResourceValue(newResource, mLibraryName);
                                mRepository.addResourceValue(mCurrentAttr);
                                break;
                            case ARRAY:
                                mArrayResourceValue =
                                        new ArrayResourceValue(newResource, mLibraryName);
                                mRepository.addResourceValue(mArrayResourceValue);
                                break;
                            default:
                                mCurrentValue = new ResourceValue(newResource, null, mLibraryName);
                                mRepository.addResourceValue(mCurrentValue);
                                break;
                        }
                    }
                }
            } else if (mDepth == 3) {
                // get the resource name
                String name = attributes.getValue(ATTR_NAME);
                if (!Strings.isNullOrEmpty(name)) {
                    if (mCurrentStyle != null) {
                        mCurrentValue =
                                new ItemResourceValue(
                                        mCurrentStyle.getNamespace(), name, null, mLibraryName);
                        mCurrentStyle.addItem((ItemResourceValue)mCurrentValue);
                    } else if (mCurrentDeclareStyleable != null) {
                        // is the attribute in the android namespace?
                        boolean isFramework = mIsFramework;
                        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                            name = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
                            isFramework = true;
                        }

                        mCurrentAttr =
                                new AttrResourceValue(
                                        new ResourceReference(ResourceType.ATTR, name, isFramework),
                                        mLibraryName);
                        mCurrentDeclareStyleable.addValue(mCurrentAttr);

                        // also add it to the repository.
                        mRepository.addResourceValue(mCurrentAttr);

                    } else if (mCurrentAttr != null) {
                        // get the enum/flag value
                        String value = attributes.getValue(ATTR_VALUE);

                        try {
                            // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
                            // use Long.decode instead.
                            mCurrentAttr.addValue(name, (int)(long)Long.decode(value));
                        } catch (NumberFormatException e) {
                            // pass, we'll just ignore this value
                        }
                    }
                } else //noinspection VariableNotUsedInsideIf
                    if (mArrayResourceValue != null) {
                    // Create a temporary resource value to hold the item's value. The value is
                    // not added to the repository, since it's just a holder. The value will be set
                    // in the `characters` method and then added to mArrayResourceValue in `endElement`.
                    mCurrentValue = new ResourceValue(TMP_REF, null);
                    }
            } else if (mDepth == 4 && mCurrentAttr != null) {
                // get the enum/flag name
                String name = attributes.getValue(ATTR_NAME);
                String value = attributes.getValue(ATTR_VALUE);

                try {
                    // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
                    // use Long.decode instead.
                    mCurrentAttr.addValue(name, (int)(long)Long.decode(value));
                } catch (NumberFormatException e) {
                    // pass, we'll just ignore this value
                }
            }
        } finally {
            super.startElement(uri, localName, qName, attributes);
        }
    }

    private static ResourceType getType(String qName, Attributes attributes) {
        String typeValue;

        // if the node is <item>, we get the type from the attribute "type"
        if (TAG_ITEM.equals(qName)) {
            typeValue = attributes.getValue(ATTR_TYPE);
        } else {
            // the type is the name of the node.
            typeValue = qName;
        }

        return ResourceType.getEnum(typeValue);
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (mCurrentValue != null) {
            String value = mCurrentValue.getValue();
            if (value == null) {
                mCurrentValue.setValue(new String(ch, start, length));
            } else {
                mCurrentValue.setValue(value + new String(ch, start, length));
            }
        }
    }
}
