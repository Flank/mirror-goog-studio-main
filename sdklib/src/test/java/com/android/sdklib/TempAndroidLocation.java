/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.sdklib;

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.EnvVar;
import com.android.sdklib.mock.MockLog;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;

/**
 * {@link org.junit.rules.TestRule} that overrides the {@link AndroidLocation} to point to temp one.
 * <p>
 * This one doesn't create a temp fake SDK (see {@link TempSdkManager}.)
 */
public class TempAndroidLocation extends ExternalResource {

    private final String mAndroidHomeName;
    private String mOldAndroidHomeProp;
    private File mAndroidHome;

    public TempAndroidLocation(String androidHomeName) {
        mAndroidHomeName = androidHomeName;
    }

    /**
     * Sets up a {@link MockLog}, a fake SDK in a temporary directory
     * and an AVD Manager pointing to an initially-empty AVD directory.
     */
    @Override
    protected void before() throws Throwable {
        makeFakeAndroidHome();
    }

    /**
     * Removes the temporary SDK and AVD directories.
     */
    @Override
    protected void after() {
        tearDownAndroidHome();
    }

    private void makeFakeAndroidHome() throws IOException {
        // First we create a temp file to "reserve" the temp directory name we want to use.
        mAndroidHome = File.createTempFile(mAndroidHomeName, null);
        // Then erase the file and make the directory
        mAndroidHome.delete();
        mAndroidHome.mkdirs();

        // Set the system property that will force AndroidLocation to use this
        mOldAndroidHomeProp = System.getProperty(EnvVar.ANDROID_SDK_HOME.getName());
        System.setProperty(EnvVar.ANDROID_SDK_HOME.getName(), mAndroidHome.getAbsolutePath());
        AndroidLocation.resetFolder();
    }

    private void tearDownAndroidHome() {
        if (mOldAndroidHomeProp == null) {
            System.clearProperty(EnvVar.ANDROID_SDK_HOME.getName());
        } else {
            System.setProperty(EnvVar.ANDROID_SDK_HOME.getName(), mOldAndroidHomeProp);
        }
        AndroidLocation.resetFolder();
        deleteDir(mAndroidHome);
    }

    /** Clear the .android home folder and reconstruct it empty. */
    private void clearAndroidHome() {
        deleteDir(mAndroidHome);
        mAndroidHome.mkdirs();
        AndroidLocation.resetFolder();
    }

    /**
     * Recursive delete directory. Mostly for fake SDKs.
     *
     * @param root directory to delete
     */
    private static void deleteDir(File root) {
        if (root.exists()) {
            for (File file : root.listFiles()) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
            root.delete();
        }
    }
}
