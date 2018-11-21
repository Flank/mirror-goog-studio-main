/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApkPusher {

    public static final String APK_DIRECTORY = Deployer.BASE_DIRECTORY + "/apks";

    private final AdbClient adb;

    public ApkPusher(AdbClient adb) {
        this.adb = adb;
    }

    public List<String> push(List<ApkEntry> fullApks) throws DeployerException {
        try {
            List<String> apkPaths = new ArrayList<>();
            adb.shell(
                    new String[] {"rm", "-r", APK_DIRECTORY, ";", "mkdir", "-p", APK_DIRECTORY},
                    null);
            Set<Apk> apks = new HashSet<>();
            for (ApkEntry file : fullApks) {
                apks.add(file.apk);
            }
            for (Apk apk : apks) {
                String target = APK_DIRECTORY + "/" + Paths.get(apk.path).getFileName();
                adb.push(apk.path, target);
                apkPaths.add(target);
            }
            return apkPaths;
        } catch (IOException e) {
            throw new DeployerException(DeployerException.Error.ERROR_PUSHING_APK, e);
        }
    }
}
