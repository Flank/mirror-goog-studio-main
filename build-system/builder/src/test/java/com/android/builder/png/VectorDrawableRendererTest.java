/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.builder.png;

import static com.android.utils.FileUtils.writeToFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.resources.Density;
import com.android.utils.FileUtils;
import com.android.utils.NullLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for {@link VectorDrawableRenderer}. */
public class VectorDrawableRendererTest {
    private static final String SIMPLE_VECTOR = "<vector></vector>";
    private static final String VECTOR_WITH_GRADIENT =
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:aapt=\"http://schemas.android.com/aapt\"\n" +
            "    android:width=\"64dp\" android:height=\"64dp\" android:viewportWidth=\"64\" android:viewportHeight=\"64\">\n" +
            "  <path android:pathData=\"M10,10h40v30h-40z\">\n" +
            "    <aapt:attr name=\"android:fillColor\">\n" +
            "      <gradient android:startY=\"10\" android:startX=\"10\" android:endY=\"40\" android:endX=\"10\">\n" +
            "      <item android:offset=\"0\" android:color=\"#FFFF0000\"/>\n" +
            "      <item android:offset=\"1\" android:color=\"#FFFFFF00\"/>\n" +
            "    </gradient>\n" +
            "    </aapt:attr>\n" +
            "  </path>\n" +
            "</vector>";
    private static final String VECTOR_WITH_FILLTYPE =
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:width=\"64dp\" android:height=\"64dp\" android:viewportWidth=\"64\" android:viewportHeight=\"64\">\n" +
            "  <path android:pathData=\"M10,10h40v30h-40z\"" +
            "       android:fillColor=\"#FF0000\"\n" +
            "       android:fillType=\"oddEven\"/>\n" +
            "</vector>";

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();
    private VectorDrawableRenderer mRenderer;
    private File mRes;
    private File mOutput;
    private Set<Density> mDensities;

    @Before
    public void setUp() throws Exception {
        mDensities = ImmutableSet.of(Density.HIGH, Density.MEDIUM, Density.LOW);
        mOutput = new File("output");
        mRenderer = new VectorDrawableRenderer(19, false, mOutput, mDensities, NullLogger::new);
        mRes = tmpFolder.newFolder("app", "src", "main", "res");
    }

    @Test
    public void commonCase() throws Exception {
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void noDensities() throws Exception {
        mRenderer = new VectorDrawableRenderer(
                19, false, mOutput, Collections.emptySet(), NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void languageQualifier() throws Exception {
        File drawable = new File(mRes, "drawable-fr");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-fr-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-fr-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-fr-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-fr-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void versionQualifier() throws Exception {
        File drawable = new File(mRes, "drawable-v16");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void densityQualifier() throws Exception {
        File drawable = new File(mRes, "drawable-hdpi");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void anyDpi() throws Exception {
        File drawable = new File(mRes, "drawable-anydpi");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void anyDpi_version() throws Exception {
        File drawable = new File(mRes, "drawable-anydpi-v16");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void noDpi() throws Exception {
        File drawable = new File(mRes, "drawable-nodpi");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-nodpi", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void noDpi_version() throws Exception {
        File drawable = new File(mRes, "drawable-nodpi-v16");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-nodpi-v16", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void needsPreprocessing() throws Exception {
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        File drawable24 = new File(mRes, "drawable-v24");
        File input24 = new File(drawable24, "icon.xml");

        File drawable28 = new File(mRes, "drawable-v28");
        File input28 = new File(drawable28, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);
        writeToFile(input24, "<vector>b</vector>");
        writeToFile(input28, "<vector>c</vector>");

        ImmutableSet.Builder<File> builder = ImmutableSet.builder();
        ImmutableSet<File> result =
                builder.addAll(mRenderer.getFilesToBeGenerated(input))
                        .addAll(mRenderer.getFilesToBeGenerated(input24))
                        .addAll(mRenderer.getFilesToBeGenerated(input28))
                        .build();

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml"),
                        FileUtils.join(mOutput, "drawable-anydpi-v24", "icon.xml"),
                        FileUtils.join(mOutput, "drawable-anydpi-v28", "icon.xml")),
                result);
    }

    @Test
    public void needsPreprocessing_v21() throws Exception {
        File drawableV21 = new File(mRes, "drawable-v21");
        File input = new File(drawableV21, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void needsPreprocessing_anydpi_v21() throws Exception {
        File drawableV21 = new File(mRes, "drawable-anydpi-v21");
        File input = new File(drawableV21, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void needsPreprocessing_v16() throws Exception {
        File drawableV16 = new File(mRes, "drawable-v16");
        File input = new File(drawableV16, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void needsPreprocessing_nonVector() throws Exception {
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input,
                "<bitmap xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "android:src=\"@drawable/icon\"/>");

        assertTrue(mRenderer.getFilesToBeGenerated(input).isEmpty());
    }

    @Test
    public void needsPreprocessing_notDrawable() throws Exception {
        File values = new File(mRes, "values");
        File input = new File(values, "strings.xml");

        writeToFile(input, "<resources></resources>");

        assertTrue(mRenderer.getFilesToBeGenerated(input).isEmpty());
    }

    @Test
    public void noGradientSdk21() throws Exception {
        mRenderer = new VectorDrawableRenderer(21, false, mOutput, mDensities, NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);

        assertTrue(mRenderer.getFilesToBeGenerated(input).isEmpty());
    }

    @Test
    public void gradientSdk21() throws Exception {
        mRenderer = new VectorDrawableRenderer(21, false, mOutput, mDensities, NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, VECTOR_WITH_GRADIENT);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v24", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void gradientSdk24() throws Exception {
        mRenderer = new VectorDrawableRenderer(24, false, mOutput, mDensities, NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, VECTOR_WITH_GRADIENT);

        assertTrue(mRenderer.getFilesToBeGenerated(input).isEmpty());
    }

    @Test
    public void multipleGradientVersions() throws Exception {
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        File drawable21 = new File(mRes, "drawable-v21");
        File input21 = new File(drawable21, "icon.xml");

        File drawable22 = new File(mRes, "drawable-v22");
        File input22 = new File(drawable22, "icon.xml");

        File drawable24 = new File(mRes, "drawable-v24");
        File input24 = new File(drawable24, "icon.xml");

        File drawable28 = new File(mRes, "drawable-v28");
        File input28 = new File(drawable28, "icon.xml");

        File drawable29 = new File(mRes, "drawable-v29");
        File input29 = new File(drawable29, "icon.xml");

        writeToFile(input, VECTOR_WITH_GRADIENT);
        writeToFile(input21, VECTOR_WITH_GRADIENT);
        writeToFile(input22, VECTOR_WITH_GRADIENT);
        writeToFile(input24, VECTOR_WITH_GRADIENT);
        writeToFile(input28, VECTOR_WITH_GRADIENT);
        writeToFile(input29, VECTOR_WITH_GRADIENT);

        ImmutableList.Builder<File> result = ImmutableList.builder();
        result.addAll(mRenderer.getFilesToBeGenerated(input))
                .addAll(mRenderer.getFilesToBeGenerated(input21))
                .addAll(mRenderer.getFilesToBeGenerated(input22))
                .addAll(mRenderer.getFilesToBeGenerated(input24))
                .addAll(mRenderer.getFilesToBeGenerated(input28))
                .addAll(mRenderer.getFilesToBeGenerated(input29));

        assertEquals(
                ImmutableList.of(
                        FileUtils.join(mOutput, "drawable-hdpi/icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi/icon.png"),
                        FileUtils.join(mOutput, "drawable-ldpi/icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v21/icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi-v21/icon.png"),
                        FileUtils.join(mOutput, "drawable-ldpi-v21/icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v22/icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi-v22/icon.png"),
                        FileUtils.join(mOutput, "drawable-ldpi-v22/icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v24/icon.xml"),
                        FileUtils.join(mOutput, "drawable-anydpi-v28/icon.xml"),
                        FileUtils.join(mOutput, "drawable-anydpi-v29/icon.xml")),
                result.build());
    }

    @Test
    public void multipleGradientVersionsAndQualifiers() throws Exception {
        File drawable = new File(mRes, "drawable-port");
        File input = new File(drawable, "icon.xml");

        File drawable21 = new File(mRes, "drawable-port-v21");
        File input21 = new File(drawable21, "icon.xml");

        File drawable24 = new File(mRes, "drawable-port-v24");
        File input24 = new File(drawable24, "icon.xml");

        File drawable29 = new File(mRes, "drawable-port-v29");
        File input29 = new File(drawable29, "icon.xml");

        writeToFile(input, VECTOR_WITH_GRADIENT);
        writeToFile(input21, VECTOR_WITH_GRADIENT);
        writeToFile(input24, VECTOR_WITH_GRADIENT);
        writeToFile(input29, VECTOR_WITH_GRADIENT);

        ImmutableList.Builder<File> result = ImmutableList.builder();
        result.addAll(mRenderer.getFilesToBeGenerated(input))
                .addAll(mRenderer.getFilesToBeGenerated(input21))
                .addAll(mRenderer.getFilesToBeGenerated(input24))
                .addAll(mRenderer.getFilesToBeGenerated(input29));

        assertEquals(
                ImmutableList.of(
                        FileUtils.join(mOutput, "drawable-port-hdpi/icon.png"),
                        FileUtils.join(mOutput, "drawable-port-mdpi/icon.png"),
                        FileUtils.join(mOutput, "drawable-port-ldpi/icon.png"),
                        FileUtils.join(mOutput, "drawable-port-hdpi-v21/icon.png"),
                        FileUtils.join(mOutput, "drawable-port-mdpi-v21/icon.png"),
                        FileUtils.join(mOutput, "drawable-port-ldpi-v21/icon.png"),
                        FileUtils.join(mOutput, "drawable-port-anydpi-v24/icon.xml"),
                        FileUtils.join(mOutput, "drawable-port-anydpi-v29/icon.xml")),
                result.build());
    }

    @Test
    public void fillTypeSdk23WithoutSupportLibrary() throws Exception {
        mRenderer = new VectorDrawableRenderer(23, false, mOutput, mDensities, NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, VECTOR_WITH_FILLTYPE);

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Truth.assertThat(result)
                .containsExactly(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v24", "icon.xml"));
    }

    @Test
    public void fillTypeSdk23WithSupportLibrary() throws Exception {
        mRenderer = new VectorDrawableRenderer(23, true, mOutput, mDensities, NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, VECTOR_WITH_FILLTYPE);

        Truth.assertThat(mRenderer.getFilesToBeGenerated(input))
                .named("getFilesToBeGenerated returned")
                .isEmpty();
    }

    @Test
    public void fillTypeSdk24() throws Exception {
        mRenderer = new VectorDrawableRenderer(24, false, mOutput, mDensities, NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        writeToFile(input, VECTOR_WITH_FILLTYPE);

        Truth.assertThat(mRenderer.getFilesToBeGenerated(input))
                .named("getFilesToBeGenerated returned")
                .isEmpty();
    }

    @Test
    public void fillTypeWithVersionAndRegularVectorDefaultConfig() throws IOException {
        mRenderer = new VectorDrawableRenderer(19, false, mOutput, mDensities, NullLogger::new);
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");
        File drawableV24 = new File(mRes, "drawable-v24");
        File inputFillType = new File(drawableV24, "icon.xml");

        writeToFile(input, SIMPLE_VECTOR);
        writeToFile(inputFillType, VECTOR_WITH_FILLTYPE);

        Truth.assertThat(mRenderer.getFilesToBeGenerated(input))
                .containsExactly(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml"));

        Truth.assertThat(mRenderer.getFilesToBeGenerated(inputFillType))
                .containsExactly(FileUtils.join(mOutput, "drawable-anydpi-v24", "icon.xml"));
    }
}
