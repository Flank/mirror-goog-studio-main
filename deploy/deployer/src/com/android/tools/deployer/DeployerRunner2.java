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

import com.android.tools.deploy.swapper.InMemoryDexArchiveDatabase;
import java.io.IOException;
import java.util.ArrayList;

public class DeployerRunner2 {
    public static void main(String[] args) throws InterruptedException, IOException {
        String[] oldapkLoc = {
            "/usr/local/google/home/acleung/instant-run-tests/dexter/acleung/old.apk"
        };
        String[] newApkLoc = {
            "/usr/local/google/home/acleung/instant-run-tests/dexter/acleung/new.apk"
        };

        ArrayList<String> oldApks = new ArrayList();
        for (int i = 0; i < oldapkLoc.length; i++) {
            oldApks.add(oldapkLoc[i]);
        }

        ArrayList<String> newApks = new ArrayList();
        for (int i = 0; i < newApkLoc.length; i++) {
            newApks.add(newApkLoc[i]);
        }

        InMemoryDexArchiveDatabase db = new InMemoryDexArchiveDatabase();

        Deployer deployer =
                new Deployer(
                        "com.example.acleung.myapplication",
                        oldApks,
                        new InstallerNotifier(),
                        new AdbCmdline(),
                        db);

        deployer.fullSwap();

        deployer =
                new Deployer(
                        "com.example.acleung.myapplication",
                        newApks,
                        new InstallerNotifier(),
                        new AdbCmdline(),
                        db);

        deployer.fullSwap();
    }
}
