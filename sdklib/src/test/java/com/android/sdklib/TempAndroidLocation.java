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
import com.android.sdklib.internal.avd.AvdManager;

import com.android.utils.FileUtils;
import org.junit.rules.ExternalResource;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * {@link org.junit.rules.TestRule} that overrides the {@link AndroidLocation} to point to temp one.
 * <p>
 * This one doesn't create a temp fake SDK (see {@link TempSdkManager}.)
 * Instead this is about setting up a temporary folder for android data as AVDs and other stored config files.
 */
public class TempAndroidLocation extends ExternalResource {

    private final String mAndroidConfigHomeName;
    private String mOldAndroidConfigHomeProp;
    private String mOldAvdHomeProp;
    private File mAndroidConfigHome;
    private File mAvdHome;

    public TempAndroidLocation(String androidConfigHomeName) {
        mAndroidConfigHomeName = androidConfigHomeName;
    }

    /**
     * Sets up a fake config folder in a temporary directory,
     * and an AVD Manager pointing to an initially-empty AVD directory.
     */
    @Override
    protected void before() throws Throwable {
        makeFakeAndroidConfigHome();
    }

    /**
     * Removes the temporary directories for AVDs and other config data.
     */
    @Override
    protected void after() {
        tearDownAndroidConfigHome();
    }

    private void makeFakeAndroidConfigHome() throws Exception {
        // First we create a temp file to "reserve" the temp directory name we want to use.
        mAndroidConfigHome = File.createTempFile(mAndroidConfigHomeName, null);
        mAvdHome = File.createTempFile("avd_" + mAndroidConfigHomeName, null);
        // Then erase the file and make the directory
        mAndroidConfigHome.delete();
        mAndroidConfigHome.mkdirs();
        mAvdHome.delete();
        mAvdHome.mkdirs();

        // Set the system property that will force AndroidLocation to use this
        mOldAndroidConfigHomeProp = System.getProperty(EnvVar.ANDROID_SDK_HOME.getName());
        mOldAvdHomeProp = System.getProperty(EnvVar.ANDROID_AVD_HOME.getName());
        System.setProperty(EnvVar.ANDROID_SDK_HOME.getName(), mAndroidConfigHome.getAbsolutePath());
        System.setProperty(EnvVar.ANDROID_AVD_HOME.getName(), mAvdHome.getAbsolutePath());
        AndroidLocation.resetFolder();

        // Assert that we are using the ANDROID_AVD_HOME in AndroidLocation and AvdManager:
        assertEquals(FileUtils.toSystemIndependentPath(mAvdHome.getPath() + File.separator),
                     FileUtils.toSystemDependentPath(AndroidLocation.getAvdFolder()));
        assertEquals(FileUtils.toSystemIndependentPath(mAvdHome.getPath() + File.separator),
                     FileUtils.toSystemDependentPath(AvdManager.getBaseAvdFolder()));
    }

    private void tearDownAndroidConfigHome() {
        if (mOldAndroidConfigHomeProp == null) {
            System.clearProperty(EnvVar.ANDROID_SDK_HOME.getName());
        } else {
            System.setProperty(EnvVar.ANDROID_SDK_HOME.getName(), mOldAndroidConfigHomeProp);
        }
        AndroidLocation.resetFolder();
        deleteDir(mAndroidConfigHome);
    }

    /** Clear the .android home folder and reconstruct it empty. */
    private void clearAndroidHome() {
        deleteDir(mAndroidConfigHome);
        mAndroidConfigHome.mkdirs();
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
