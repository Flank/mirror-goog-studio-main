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

package com.android.builder.core;

import static com.android.SdkConstants.ATTR_EXTRACT_NATIVE_LIBS;
import static com.android.SdkConstants.ATTR_FUNCTIONAL_TEST;
import static com.android.SdkConstants.ATTR_HANDLE_PROFILING;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_SPLIT;
import static com.android.SdkConstants.ATTR_TARGET_PACKAGE;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.ATTR_VERSION_CODE;
import static com.android.SdkConstants.ATTR_VERSION_NAME;
import static com.android.SdkConstants.NS_RESOURCES;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_INSTRUMENTATION;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.builder.core.DefaultManifestParser.Attribute.APP_EXTRACT_NATIVE_LIBS;
import static com.android.builder.core.DefaultManifestParser.Attribute.INST_FUNCTIONAL_TEST;
import static com.android.builder.core.DefaultManifestParser.Attribute.INST_HANDLE_PROF;
import static com.android.builder.core.DefaultManifestParser.Attribute.INST_LABEL;
import static com.android.builder.core.DefaultManifestParser.Attribute.INST_NAME;
import static com.android.builder.core.DefaultManifestParser.Attribute.INST_TARGET_PKG;
import static com.android.builder.core.DefaultManifestParser.Attribute.MIN_SDK_VERSION;
import static com.android.builder.core.DefaultManifestParser.Attribute.PACKAGE;
import static com.android.builder.core.DefaultManifestParser.Attribute.SPLIT;
import static com.android.builder.core.DefaultManifestParser.Attribute.TARGET_SDK_VERSION;
import static com.android.builder.core.DefaultManifestParser.Attribute.VERSION_CODE;
import static com.android.builder.core.DefaultManifestParser.Attribute.VERSION_NAME;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.manifmerger.PlaceholderHandler;
import com.android.utils.XmlUtils;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Map;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Implementation of the {@link ManifestAttributeSupplier}.
 *
 * <p>This is meant to be a quick parser to create the building model.
 */
public class DefaultManifestParser implements ManifestAttributeSupplier {

    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();

    static {
        XmlUtils.configureSaxFactory(PARSER_FACTORY, true, false);
    }


    @NonNull private final File manifestFile;

    @NonNull
    private final Map<Attribute, String> attributeValues = Maps.newEnumMap(Attribute.class);

    private boolean initialized = false;

    /**
     * Builds instance of the parser, and parses the supplied file.
     */
    public DefaultManifestParser(@NonNull File manifestFile) {
        this.manifestFile = manifestFile;
    }

    /**
     * Gets the package name for the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getPackage() {
        init();
        return attributeValues.get(PACKAGE);
    }

    /**
     * Gets the split name for the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getSplit() {
        init();

        return attributeValues.get(SPLIT);
    }

    /**
     * Gets the version name for the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getVersionName() {
        init();
        return attributeValues.get(VERSION_NAME);
    }

    /**
     * Gets the version code for the manifest file processed by this parser.
     */
    @Override
    public int getVersionCode() {
        init();
        String versionCode = attributeValues.get(VERSION_CODE);
        return (int) parseIntValueOrDefault(versionCode, -1, -1);
    }

    /**
     * Gets the minimum sdk version for the manifest file processed by this parser.
     */
    @Override
    @NonNull
    public Object getMinSdkVersion() {
        init();
        String minSdkVersion = attributeValues.get(MIN_SDK_VERSION);
        return parseIntValueOrDefault(minSdkVersion, minSdkVersion, 1);
    }

    /**
     * Gets the target sdk version for the manifest file processed by this parser.
     */
    @Override
    @NonNull
    public Object getTargetSdkVersion() {
        init();
        String targetSdkVersion = attributeValues.get(TARGET_SDK_VERSION);
        return parseIntValueOrDefault(targetSdkVersion, targetSdkVersion, -1);
    }

    /**
     * Gets the instrumentation runner for the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getInstrumentationRunner() {
        init();
        return attributeValues.get(INST_NAME);
    }

    /**
     * Gets the target package for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getTargetPackage() {
        init();
        return attributeValues.get(INST_TARGET_PKG);
    }

    /**
     * Gets the functionalTest for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public Boolean getFunctionalTest() {
        init();
        String functionalTest = attributeValues.get(INST_FUNCTIONAL_TEST);
        return parseBoolean(functionalTest);
    }

    /**
     * Gets the handleProfiling for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public Boolean getHandleProfiling() {
        init();
        String handleProfiling = attributeValues.get(INST_HANDLE_PROF);
        return parseBoolean(handleProfiling);
    }

    /**
     * Gets the testLabel for instrumentation in the manifest file processed by this parser.
     */
    @Nullable
    @Override
    public String getTestLabel() {
        init();
        return attributeValues.get(INST_LABEL);
    }

    @Nullable
    @Override
    public Boolean getExtractNativeLibs() {
        init();
        String extractNativeLibs = attributeValues.get(Attribute.APP_EXTRACT_NATIVE_LIBS);
        return parseBoolean(extractNativeLibs);
    }

    /**
     * If {@code value} is {@code null}, it returns {@code ifNull}. Otherwise it tries to parse the
     * {@code value} to {@link Integer}. If parsing the {@link Integer} fails, it will return {@code
     * ifNotInt} value.
     *
     * @param value    to be parsed
     * @param ifNotInt value returned if value is non {@code null} and it is not {@code int} value
     * @param ifNull   value returned if supplied value is {@code null}
     * @return final value according to the rules described above
     */
    private static Object parseIntValueOrDefault(String value, Object ifNotInt, Object ifNull) {
        if (value != null) {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException ignored) {
                return ifNotInt;
            }
        } else {
            return ifNull;
        }
    }

    @Nullable
    private static Boolean parseBoolean(String value) {
        if (value != null) {
            return Boolean.parseBoolean(value);
        } else {
            return null;
        }
    }

    enum Attribute {
        SPLIT,
        PACKAGE,
        VERSION_CODE,
        VERSION_NAME,
        INST_LABEL,
        INST_FUNCTIONAL_TEST,
        INST_NAME,
        INST_HANDLE_PROF,
        INST_TARGET_PKG,
        MIN_SDK_VERSION,
        TARGET_SDK_VERSION,
        APP_EXTRACT_NATIVE_LIBS;
    }

    /** Parse the file and store the result in a map. */
    private void init() {
        if (!initialized && manifestFile.isFile()) {
            DefaultHandler handler =
                    new DefaultHandler() {
                        @Override
                        public void startElement(
                                String uri, String localName, String qName, Attributes attributes)
                                throws SAXException {
                            if (uri == null || uri.isEmpty()) {
                                if (TAG_MANIFEST.equals(localName)) {
                                    putValue(SPLIT, attributes.getValue("", ATTR_SPLIT));
                                    putValue(PACKAGE, attributes.getValue("", ATTR_PACKAGE));
                                    putValue(
                                            VERSION_CODE,
                                            attributes.getValue(NS_RESOURCES, ATTR_VERSION_CODE));
                                    putValue(
                                            VERSION_NAME,
                                            attributes.getValue(NS_RESOURCES, ATTR_VERSION_NAME));
                                } else if (TAG_INSTRUMENTATION.equals(localName)) {
                                    putValue(
                                            INST_LABEL,
                                            attributes.getValue(NS_RESOURCES, ATTR_LABEL));
                                    putValue(
                                            INST_FUNCTIONAL_TEST,
                                            attributes.getValue(
                                                    NS_RESOURCES, ATTR_FUNCTIONAL_TEST));
                                    putValue(
                                            INST_NAME,
                                            attributes.getValue(NS_RESOURCES, ATTR_NAME));
                                    putValue(
                                            INST_HANDLE_PROF,
                                            attributes.getValue(
                                                    NS_RESOURCES, ATTR_HANDLE_PROFILING));
                                    putValue(
                                            INST_TARGET_PKG,
                                            attributes.getValue(NS_RESOURCES, ATTR_TARGET_PACKAGE));
                                } else if (TAG_USES_SDK.equals(localName)) {
                                    putValue(
                                            MIN_SDK_VERSION,
                                            attributes.getValue(
                                                    NS_RESOURCES, ATTR_MIN_SDK_VERSION));
                                    putValue(
                                            TARGET_SDK_VERSION,
                                            attributes.getValue(
                                                    NS_RESOURCES, ATTR_TARGET_SDK_VERSION));
                                } else if (TAG_APPLICATION.equals(localName)) {
                                    putValue(
                                            APP_EXTRACT_NATIVE_LIBS,
                                            attributes.getValue(
                                                    NS_RESOURCES, ATTR_EXTRACT_NATIVE_LIBS));
                                }
                            }
                        }
                    };

            try {
                SAXParser saxParser = XmlUtils.createSaxParser(PARSER_FACTORY);
                saxParser.parse(manifestFile, handler);
                initialized = true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void putValue(@NonNull Attribute attribute, @Nullable String value) {
        if (value != null && !PlaceholderHandler.isPlaceHolder(value)) {
            attributeValues.put(attribute, value);
        }
    }
}
