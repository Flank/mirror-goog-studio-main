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

package com.android.instantapp.sdk;

import static com.google.common.io.Files.createTempDir;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Utils to test. */
public class InstantAppSdkTests {

    @NonNull
    public static File getInstantAppSdk() throws IOException {
        String path = "whsdk-testnew";
        File jarFile =
                new File(
                        InstantAppSdkTests.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath());

        if (jarFile.isFile()) {
            // If running with bazel or any other that compresses the resources in a jar.
            File instantAppSdk = createTempDir();
            JarFile jar = new JarFile(jarFile);
            Enumeration enumEntries = jar.entries();
            while (enumEntries.hasMoreElements()) {
                JarEntry file = (JarEntry) enumEntries.nextElement();
                File f = new File(instantAppSdk + java.io.File.separator + file.getName());
                if (file.isDirectory()) {
                    // If it iss a directory, create it
                    f.mkdir();
                    continue;
                }
                InputStream is = jar.getInputStream(file);
                FileOutputStream fos = new FileOutputStream(f);
                while (is.available() > 0) {
                    // Copy content from input stream to file output stream.
                    fos.write(is.read());
                }
                fos.close();
                is.close();
            }
            jar.close();
            return new File(instantAppSdk, path);
        } else {
            // If running in the IDE, the resources are not compressed in a jar, just return it.
            return new File(Resources.getResource(InstantAppSdkTests.class, "/" + path).getFile());
        }
    }

    /** Generates a mock device that can be configured. */
    public static class DeviceGenerator {
        @NonNull private final IDevice myDevice;

        public DeviceGenerator() {
            myDevice = mock(IDevice.class);
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setArchitectures(@NonNull String... archs) {
            when(myDevice.getAbis()).thenReturn(Lists.newArrayList(archs));
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setApiLevel(
                int apiLevel, @Nullable String codename) {
            when(myDevice.getVersion()).thenReturn(new AndroidVersion(apiLevel, codename));
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setVersionOfPackage(
                @NonNull String pkgName, long version) throws Throwable {
            String shellCommand = "dumpsys package " + pkgName;
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] output =
                                        ("versionCode=" + version + " \n")
                                                .getBytes(Charset.defaultCharset());
                                receiver.addOutput(output, 0, output.length);
                                receiver.flush();
                                return null;
                            })
                    .when(myDevice)
                    .executeShellCommand(eq(shellCommand), notNull());
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setGoogleAccountLogged() throws Throwable {
            String shellCommand = "dumpsys account";
            doAnswer(
                            invocation -> {
                                IShellOutputReceiver receiver = invocation.getArgument(1);
                                byte[] output =
                                        ("Account {name=bla@google.com, type=com.google}\n")
                                                .getBytes(Charset.defaultCharset());
                                receiver.addOutput(output, 0, output.length);
                                receiver.flush();
                                return null;
                            })
                    .when(myDevice)
                    .executeShellCommand(eq(shellCommand), notNull());
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setOsBuildType(@NonNull String osBuildType) {
            when(myDevice.getProperty("ro.build.tags")).thenReturn(osBuildType);
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setManufacturer(@NonNull String manufacturer) {
            when(myDevice.getProperty("ro.product.manufacturer")).thenReturn(manufacturer);
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setAndroidDevice(@NonNull String androidDevice) {
            when(myDevice.getProperty("ro.product.device")).thenReturn(androidDevice);
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setProduct(@NonNull String product) {
            when(myDevice.getProperty("ro.product.name")).thenReturn(product);
            return this;
        }

        @NonNull
        public InstantAppSdkTests.DeviceGenerator setHardware(@NonNull String hardware) {
            when(myDevice.getProperty("ro.hardware")).thenReturn(hardware);
            return this;
        }

        @NonNull
        public IDevice getDevice() {
            return myDevice;
        }
    }
}
