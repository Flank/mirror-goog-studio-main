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
package com.android.instantapp.provision;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.util.*;

/**
 * Represents the model of the metadata found in the Instant Apps Sdk and useful . This class can be
 * initialized passing the Sdk containing an xml manifest file.
 */
class Metadata {
    private final long mySdkVersionCode;
    @NonNull private final String mySdkVersionName;
    @NonNull private final Map<Arch, List<ApkInfo>> myApks;
    @NonNull private final Set<Device> myEnabledDevices;
    @NonNull private final List<GServicesOverride> myGServicesOverrides;

    Metadata(
            long sdkVersionCode,
            @NonNull String sdkVersionName,
            @NonNull Map<Arch, List<ApkInfo>> apks,
            @NonNull Set<Device> enabledDevices,
            @NonNull List<GServicesOverride> gServicesOverrides) {
        mySdkVersionCode = sdkVersionCode;
        mySdkVersionName = sdkVersionName;
        myApks = apks;
        myEnabledDevices = enabledDevices;
        myGServicesOverrides = gServicesOverrides;
    }

    boolean isSupportedArch(@NonNull String arch) {
        return myApks.keySet().contains(Arch.create(arch));
    }

    boolean isSupportedDevice(@NonNull Device device) {
        for (Device enabledDevice : myEnabledDevices) {
            if (enabledDevice.matches(device)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    List<ApkInfo> getApks(@NonNull Arch arch) {
        List<ApkInfo> apks = new LinkedList<>();
        apks.addAll(myApks.get((Arch.DEFAULT)));
        if (arch != Arch.DEFAULT) {
            apks.addAll(myApks.get(arch));
        }
        return apks;
    }

    /**
     * @param device a {@link Device} representing the device being provisioned, either a real one
     *     or an emulator.
     * @return all the gServicesOverrides that should be applied to the specific device.
     */
    @NonNull
    List<GServicesOverride> getGServicesOverrides(@NonNull Device device) {
        List<GServicesOverride> gServicesOverrides = new ArrayList<>();
        for (GServicesOverride gServicesOverride : myGServicesOverrides) {
            Set<Device> gServiceDevices = gServicesOverride.getDevices();
            if (gServiceDevices.isEmpty()) {
                gServicesOverrides.add(gServicesOverride);
            }
            for (Device gServiceDevice : gServiceDevices) {
                if (gServiceDevice.matches(device)) {
                    gServicesOverrides.add(gServicesOverride);
                    break;
                }
            }
        }
        return gServicesOverrides;
    }

    enum Arch {
        DEFAULT("default"),
        ARMEABI("armeabi"),
        ARMEABI_V7A("armeabi-v7a"),
        ARM64_V8A("arm64-v8a"),
        X86("x86"),
        X86_64("x86_64"),
        MIPS("mips"),
        MIPS64("mips64"),
        UNKNOWN("unknown");

        @NonNull private final String myArchName;

        Arch(@NonNull String archName) {
            myArchName = archName;
        }

        @NonNull
        public static Arch create(@NonNull String name) {
            for (Arch value : Arch.values()) {
                if (value.toString().compareTo(name) == 0) {
                    return value;
                }
            }
            return UNKNOWN;
        }

        @Override
        public String toString() {
            return myArchName;
        }
    }

    static class ApkInfo {
        @NonNull private final String myPkgName;
        @NonNull private final File myApk;
        @NonNull private final Arch myArch;
        private final long myVersionCode;

        ApkInfo(@NonNull String pkgName, @NonNull File apk, @NonNull Arch arch, long version) {
            myPkgName = pkgName;
            myApk = apk;
            myArch = arch;
            myVersionCode = version;
        }

        @NonNull
        public String getPkgName() {
            return myPkgName;
        }

        @NonNull
        public File getApk() {
            return myApk;
        }

        @NonNull
        public Arch getArch() {
            return myArch;
        }

        public long getVersionCode() {
            return myVersionCode;
        }
    }

    static class Device {
        // ro.product.manufacturer
        @Nullable private final String myManufacturer;
        // ro.product.device
        @Nullable private final String myAndroidDevice;
        // ro.build.version.sdk
        @NonNull private final Set<Integer> myApiLevels;
        // ro.product.name
        @Nullable private final String myProduct;
        // ro.hardware
        @Nullable private final String myHardware;

        Device(
                @Nullable String manufacturer,
                @Nullable String androidDevice,
                @NonNull Set<Integer> apiLevels,
                @Nullable String product,
                @Nullable String hardware) {
            myManufacturer = manufacturer;
            myAndroidDevice = androidDevice;
            myApiLevels = apiLevels;
            myProduct = product;
            myHardware = hardware;
        }

        @Override
        public String toString() {
            return "{myManufacturer: "
                    + myManufacturer
                    + ", myAndroidDevice: "
                    + myAndroidDevice
                    + ", myApiLevels: "
                    + myApiLevels
                    + ", myProduct: "
                    + myProduct
                    + ", myHardware: "
                    + myHardware
                    + "}";
        }

        @Nullable
        public String getManufacturer() {
            return myManufacturer;
        }

        @Nullable
        public String getAndroidDevice() {
            return myAndroidDevice;
        }

        @NonNull
        public Set<Integer> getApiLevels() {
            return myApiLevels;
        }

        @Nullable
        public String getProduct() {
            return myProduct;
        }

        @Nullable
        public String getHardware() {
            return myHardware;
        }

        /**
         * Analyses if {@code other} device is a subset of the devices represented by {@code this}.
         *
         * @param other {@link Device} to be checked if belongs to this set of {@link Device}s.
         * @return {@code boolean} indicating if it matches.
         */
        public boolean matches(@NonNull Device other) {
            return (myManufacturer == null
                            || (other.myManufacturer != null
                                    && myManufacturer.compareTo(other.myManufacturer) == 0))
                    && (myAndroidDevice == null
                            || (other.myAndroidDevice != null
                                    && myAndroidDevice.compareTo(other.myAndroidDevice) == 0))
                    && (myApiLevels.isEmpty() || myApiLevels.containsAll(other.myApiLevels))
                    && (myProduct == null
                            || (other.myProduct != null
                                    && myProduct.compareTo(other.myProduct) == 0))
                    && (myHardware == null
                            || (other.myHardware != null
                                    && myHardware.compareTo(other.myHardware) == 0));
        }
    }

    static class GServicesOverride {
        @NonNull private final Set<Device> myDevices;
        @NonNull private final String myKey;
        @NonNull private final String myValue;

        GServicesOverride(
                @NonNull Set<Device> devices, @NonNull String key, @NonNull String value) {
            myDevices = devices;
            myKey = key;
            myValue = value;
        }

        /** If the set of devices is empty, the override should be applied to every device. */
        @NonNull
        public Set<Device> getDevices() {
            return myDevices;
        }

        @NonNull
        public String getKey() {
            return myKey;
        }

        @NonNull
        public String getValue() {
            return myValue;
        }
    }
}
