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
package com.android.assetstudiolib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Matchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class MaterialDesignIconsTest {

    @Test
    public void getPathForBasename() {
        Object expected = "images/material_design_icons/places/ic_rv_hookup_black_24dp.xml";
        assertEquals(expected, MaterialDesignIcons.getPathForBasename("ic_rv_hookup_black_24dp"));
    }

    @Test
    public void getBasenameToPathMap() {
        Object expected = Collections.singletonMap(
                "ic_search_black_24dp",
                "images/material_design_icons/action/ic_search_black_24dp.xml");

        assertEquals(expected, MaterialDesignIcons.getBasenameToPathMap(mockGenerator()));
    }

    @Test
    public void getBasenameToPathMapThrowsIllegalArgumentException() {
        Function<String, Iterator<String>> generator = mockGenerator();

        Mockito.when(generator.apply("images/material_design_icons/device/"))
                .thenReturn(Collections.singletonList("ic_search_black_24dp.xml").iterator());

        try {
            MaterialDesignIcons.getBasenameToPathMap(generator);
            fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @NonNull
    private static Function<String, Iterator<String>> mockGenerator() {
        @SuppressWarnings("unchecked")
        Function<String, Iterator<String>> generator
                = (Function<String, Iterator<String>>) Mockito.mock(Function.class);

        Mockito.when(generator.apply(Matchers.any()))
                .thenReturn(Collections.emptyIterator());

        Mockito.when(generator.apply("images/material_design_icons/action/"))
                .thenReturn(Collections.singletonList("ic_search_black_24dp.xml").iterator());

        return generator;
    }

    @Test
    public void getCategoriesNullUrl() {
        assertEquals(Collections.emptyList(), MaterialDesignIcons.getCategories(null));
    }

    @Test
    public void getCategoriesUrlProtocolEqualsHttps() throws MalformedURLException {
        Object actual = MaterialDesignIcons.getCategories(new URL("https://www.google.com/"));
        assertEquals(Collections.emptyList(), actual);
    }

    @Test
    public void getCategoriesFromFile() {
        Object actual = MaterialDesignIcons.getCategoriesFromFile(Mockito.mock(File.class));
        assertEquals(Collections.emptyList(), actual);
    }

    @Test
    public void getCategoriesFromJar() {
        JarFile jar = Mockito.mock(JarFile.class);

        Mockito.when(jar.stream()).thenReturn(Stream.of(
                new JarEntry("images/material_design_icons/alert/"),
                new JarEntry("images/material_design_icons/action/ic_3d_rotation_black_24dp.xml"),
                new JarEntry("images/material_design_icons/action/"),
                new JarEntry("images/material_design_icons/")));

        Object actual = MaterialDesignIcons.getCategoriesFromJar(jar);
        assertEquals(Arrays.asList("action", "alert"), actual);
    }
}
