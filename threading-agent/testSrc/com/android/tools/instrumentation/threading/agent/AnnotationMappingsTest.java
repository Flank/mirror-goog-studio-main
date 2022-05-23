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

    @Test
    public void uiThreadAnnotationMapping() {
        assertThat(
                        AnnotationMappings.create()
                                .isThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/UiThread;"))
                .isTrue();
        assertThat(
                        AnnotationMappings.create()
                                .getCheckerMethodForThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/UiThread;"))
                .isEqualTo(
                        Optional.of(
                                new CheckerMethodRef(
                                        "com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline",
                                        "verifyOnUiThread")));
    }

    @Test
    public void workerThreadAnnotationMapping() {
        assertThat(
                        AnnotationMappings.create()
                                .isThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/WorkerThread;"))
                .isTrue();
        assertThat(
                        AnnotationMappings.create()
                                .getCheckerMethodForThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/WorkerThread;"))
                .isEqualTo(Optional.empty());
    }

    @Test
    public void slowThreadAnnotationMapping() {
        assertThat(
                        AnnotationMappings.create()
                                .isThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/Slow;"))
                .isTrue();
        assertThat(
                        AnnotationMappings.create()
                                .getCheckerMethodForThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/Slow;"))
                .isEqualTo(Optional.empty());
    }

    @Test
    public void anyThreadAnnotationMapping() {
        assertThat(
                        AnnotationMappings.create()
                                .isThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/AnyThread;"))
                .isTrue();
        assertThat(
                        AnnotationMappings.create()
                                .getCheckerMethodForThreadingAnnotation(
                                        "Lcom/android/annotations/concurrency/AnyThread;"))
                .isEqualTo(Optional.empty());
    }

    @Test
    public void nonThreadingAnnotation_callToIsThreadingAnnotation_returnsFalse() {
        assertThat(AnnotationMappings.create().isThreadingAnnotation("random_annotation_abc"))
                .isFalse();
    }

    @Test
    public void nonThreadingAnnotation_callToGetCheckerMethod_throws() {
        try {
            AnnotationMappings.create()
                    .getCheckerMethodForThreadingAnnotation("random_annotation_abc");
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(Throwables.getRootCause(e).getMessage())
                    .contains("not a threading annotation");
        }
    }
}
