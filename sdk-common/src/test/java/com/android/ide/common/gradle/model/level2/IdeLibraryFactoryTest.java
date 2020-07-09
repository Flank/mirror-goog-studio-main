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
package com.android.ide.common.gradle.model.level2;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.Nullable;
import com.android.builder.model.JavaLibrary;
import com.android.ide.common.gradle.model.stubs.JavaLibraryStub;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeLibraryFactory}. */
public class IdeLibraryFactoryTest {
    private IdeLibraryFactory myLibraryFactory;

    @Before
    public void setUp() throws Exception {
        myLibraryFactory = new IdeLibraryFactory();
    }

    @Test
    public void createFromJavaLibrary() {
        // Verify JavaLibrary of module dependency returns instance of IdeModuleLibrary.
        IdeLibrary moduleLibrary = myLibraryFactory.create(new JavaLibraryStub());
        assertThat(moduleLibrary).isInstanceOf(IdeModuleLibrary.class);

        // Verify JavaLibrary of jar dependency returns instance of IdeJavaLibrary.
        JavaLibrary javaLibrary =
                new com.android.ide.common.gradle.model.stubs.JavaLibraryStub() {
                    @Override
                    @Nullable
                    public String getProject() {
                        return null;
                    }
                };
        assertThat(myLibraryFactory.create(javaLibrary)).isInstanceOf(IdeJavaLibrary.class);
    }

    @Test
    public void createFromString() {
        assertThat(myLibraryFactory.create("lib", ":lib@@:", "/rootDir/lib"))
                .isInstanceOf(IdeModuleLibrary.class);
    }
}
