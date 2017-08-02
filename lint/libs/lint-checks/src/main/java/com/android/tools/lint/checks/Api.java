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
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.utils.XmlUtils;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;

/**
 * Main entry point for API description.
 *
 * To create the {@code Api}, use {@link #parseApi(File)}.
 */
public class Api {
    /**
     * Parses simplified API file.
     * @param apiFile the file to read
     * @return a new ApiInfo
     * @throws RuntimeException in case of an error
     */
    @NonNull
    public static Api parseApi(File apiFile) {
        try (InputStream inputStream = new FileInputStream(apiFile)) {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            XmlUtils.configureSaxFactory(parserFactory, false, false);
            SAXParser parser = XmlUtils.createSaxParser(parserFactory);
            ApiParser apiParser = new ApiParser();
            parser.parse(inputStream, apiParser);
            inputStream.close();
            return new Api(apiParser.getClasses(), apiParser.getContainers());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<String, ApiClass> mClasses;
    private final Map<String, ApiClassOwner> mContainers;

    private Api(
            @NonNull Map<String, ApiClass> classes,
            @NonNull Map<String, ApiClassOwner> containers) {
        mClasses = Collections.unmodifiableMap(new MyHashMap(classes));
        mContainers = Collections.unmodifiableMap(new MyHashMap(containers));
    }

    ApiClass getClass(String fqcn) {
        return mClasses.get(fqcn);
    }

    Map<String, ApiClass> getClasses() {
        return mClasses;
    }

    Map<String, ApiClassOwner> getContainers() {
        return mContainers;
    }

    /** The hash map that doesn't distinguish between '.', '/', and '$' in the key string. */
    private static class MyHashMap<V> extends THashMap<String, V> {
        private static final TObjectHashingStrategy<String> myHashingStrategy =
                new TObjectHashingStrategy<String>() {
            @Override
            public int computeHashCode(String str) {
                int h = 0;
                for (int i = 0; i < str.length(); i++) {
                    char c = str.charAt(i);
                    c = normalizeSeparator(c);
                    h = 31 * h + c;
                }
                return h;
            }

            @Override
            public boolean equals(String s1, String s2) {
                if (s1.length() != s2.length()) {
                    return false;
                }
                for (int i = 0; i < s1.length(); i++) {
                    if (normalizeSeparator(s1.charAt(i)) != normalizeSeparator(s2.charAt(i))) {
                        return false;
                    }
                }
                return true;
            }
        };

        private static char normalizeSeparator(char c) {
            if (c == '/' || c == '$') {
                c = '.';
            }
            return c;
        }

        MyHashMap(Map<String, V> data) {
            super(data, myHashingStrategy);
        }
    }
}
