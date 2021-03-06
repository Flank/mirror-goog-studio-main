/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.deployer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class InstallOptionsTest {

    @Test
    public void testFull() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        optionsBuilder.setInstallFullApk();
        List<String> options = optionsBuilder.build().getFlags();
        assertTrue(options.contains("--full"));
        assertTrue(options.size() == 1);
    }

    @Test
    public void testPermissions() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        optionsBuilder.setGrantAllPermissions();
        List<String> options = optionsBuilder.build().getFlags();
        assertTrue(options.contains("-g"));
        assertTrue(options.size() == 1);
    }

    @Test
    public void testForceQueryable() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        optionsBuilder.setForceQueryable();
        List<String> options = optionsBuilder.build().getFlags();
        assertTrue(options.contains("--force-queryable"));
        assertTrue(options.size() == 1);
    }

    @Test
    public void testDebuggable() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        optionsBuilder.setAllowDebuggable();
        List<String> options = optionsBuilder.build().getFlags();
        assertTrue(options.contains("-t"));
        assertTrue(options.size() == 1);
    }

    @Test
    public void testCurrentUser() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        optionsBuilder.setInstallOnCurrentUser();
        List<String> options = optionsBuilder.build().getFlags();
        int ix = options.indexOf("--user");
        assertTrue(ix != -1);
        assertTrue(ix + 1 < options.size());
        assertEquals("current", options.get(ix + 1));
    }

    @Test
    public void testUserParameter() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        optionsBuilder.setUserInstallOptions("-X bla");
        List<String> options = optionsBuilder.build().getFlags();
        assertTrue(options.contains("-X bla"));
        assertTrue(options.size() == 1);
    }

    @Test
    public void testUserParameters() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        String[] optionsStrings = {"-a", "-b"};
        optionsBuilder.setUserInstallOptions(optionsStrings);
        List<String> options = optionsBuilder.build().getFlags();
        assertTrue(options.contains("-a"));
        assertTrue(options.contains("-b"));
        assertTrue(options.indexOf("-b") == options.indexOf("-a") + 1);
        assertTrue(options.size() == 2);
    }

    @Test
    public void testParameterCombined() {
        InstallOptions.Builder optionsBuilder = InstallOptions.builder();
        optionsBuilder.setAllowDebuggable();
        optionsBuilder.setGrantAllPermissions();
        optionsBuilder.setInstallFullApk();
        optionsBuilder.setUserInstallOptions("-c");
        String[] optionsStrings = {"-a", "-b"};
        optionsBuilder.setUserInstallOptions(optionsStrings);

        List<String> options = optionsBuilder.build().getFlags();
        assertTrue(options.contains("--full"));
        assertTrue(options.contains("-g"));
        assertTrue(options.contains("-t"));
        assertTrue(options.contains("-a"));
        assertTrue(options.contains("-b"));
        assertTrue(options.contains("-c"));
        assertTrue(options.size() == 6);
    }
}
