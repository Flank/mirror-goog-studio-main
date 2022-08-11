/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools;

import com.android.tools.deployer.Sites;
import org.junit.Assert;
import org.junit.Test;

public class TestSites {
    @Test
    public void testSites() {
        String pkg = "foo";

        String appData = Sites.appData(pkg);
        Assert.assertEquals("/data/data/foo/", appData);

        String codeCache = Sites.appCodeCache(pkg);
        Assert.assertEquals("/data/data/foo/code_cache/", codeCache);

        String studio = Sites.appStudio(pkg);
        Assert.assertEquals("/data/data/foo/code_cache/.studio/", studio);

        String logs = Sites.appLog(pkg);
        Assert.assertEquals("/data/data/foo/.agent-logs/", logs);

        String startup = Sites.appStartupAgent(pkg);
        Assert.assertEquals("/data/data/foo/code_cache/startup_agents/", startup);

        String overlays = Sites.appOverlays(pkg);
        Assert.assertEquals("/data/data/foo/code_cache/.overlay/", overlays);

        String liveLiteral = Sites.appLiveLiteral(pkg);
        Assert.assertEquals("/data/data/foo/code_cache/.ll/", liveLiteral);

        String deviceStudioFolder = Sites.deviceStudioFolder();
        Assert.assertEquals("/data/local/tmp/.studio/", deviceStudioFolder);

        String installerExecutableFolder = Sites.installerExecutableFolder();
        Assert.assertEquals("/data/local/tmp/.studio/bin/", installerExecutableFolder);

        String installerTmpFolder = Sites.installerTmpFolder();
        Assert.assertEquals("/data/local/tmp/.studio/tmp/", installerTmpFolder);

        String installerBinary = Sites.installerBinary();
        Assert.assertEquals("installer", installerBinary);

        String installerPath = Sites.installerPath();
        Assert.assertEquals("/data/local/tmp/.studio/bin/installer", installerPath);
    }
}
