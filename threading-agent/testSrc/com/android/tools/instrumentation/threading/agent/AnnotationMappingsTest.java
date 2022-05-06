/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.instrumentation.threading.agent;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Throwables;
import java.util.Optional;
import org.junit.Test;

public class AnnotationMappingsTest {

    private static String ANNOTATION_1 = "ThreadingAnnotation1";

    private static String ANNOTATION_2 = "ThreadingAnnotation2";

    private static String CLASS_NAME_1 = "ClassAbc";

    private static String METHOD_NAME_1 = "methodIjk";

    @Test
    public void isThreadingAnnotation() {
        AnnotationMappings annotationMappings =
                AnnotationMappings.newBuilder()
                        .addThreadingAnnotationWithCheckerMethod(
                                ANNOTATION_1, CLASS_NAME_1, METHOD_NAME_1)
                        .addNoopThreadingAnnotation(ANNOTATION_2)
                        .build();

        assertThat(annotationMappings.isThreadingAnnotation(ANNOTATION_1)).isTrue();
        assertThat(annotationMappings.isThreadingAnnotation(ANNOTATION_2)).isTrue();
        assertThat(annotationMappings.isThreadingAnnotation("random_annotation_abc")).isFalse();
    }

    @Test
    public void getCheckerMethodForThreadingAnnotation() {
        AnnotationMappings annotationMappings =
                AnnotationMappings.newBuilder()
                        .addThreadingAnnotationWithCheckerMethod(
                                ANNOTATION_1, CLASS_NAME_1, METHOD_NAME_1)
                        .addNoopThreadingAnnotation(ANNOTATION_2)
                        .build();

        assertThat(annotationMappings.getCheckerMethodForThreadingAnnotation(ANNOTATION_1))
                .isEqualTo(Optional.of(new CheckerMethodRef(CLASS_NAME_1, METHOD_NAME_1)));
        assertThat(annotationMappings.getCheckerMethodForThreadingAnnotation(ANNOTATION_2))
                .isEqualTo(Optional.empty());
    }

    @Test
    public void getCheckerMethodForNonThreadingAnnotation_throws() {
        AnnotationMappings annotationMappings =
                AnnotationMappings.newBuilder().addNoopThreadingAnnotation(ANNOTATION_1).build();

        try {
            annotationMappings.getCheckerMethodForThreadingAnnotation("random_annotation_abc");
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(Throwables.getRootCause(e).getMessage())
                    .contains("not a threading annotation");
        }
    }
}
