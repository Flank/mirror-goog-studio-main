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

package com.android.build.gradle.external.cmake.server;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.external.cmake.CmakeUtils;
import java.io.IOException;
import org.junit.Test;

public class ServerFactoryTest {
    @Test
    public void testValidServerCreation() throws IOException {
        assertThat(ServerFactory.create(CmakeUtils.getVersion("3.7.1"))).isNotNull();
        assertThat(ServerFactory.create(CmakeUtils.getVersion("3.8.0-rc2"))).isNotNull();
    }

    @Test
    public void testInvalidServerCreation() throws IOException {
        assertThat(ServerFactory.create(CmakeUtils.getVersion("3.6.3"))).isNull();
        assertThat(ServerFactory.create(CmakeUtils.getVersion("2.3.5-rc2"))).isNull();
        assertThat(ServerFactory.create(CmakeUtils.getVersion("1.2.1"))).isNull();
    }
}
