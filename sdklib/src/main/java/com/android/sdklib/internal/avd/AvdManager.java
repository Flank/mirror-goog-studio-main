/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.sdklib.internal.avd;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.repository.api.LocalPackage;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.FileOpFileWrapper;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.DeviceManager.DeviceStatus;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.utils.GrabProcessOutput;
import com.android.utils.GrabProcessOutput.IProcessOutput;
import com.android.utils.GrabProcessOutput.Wait;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.io.Closeables;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android Virtual Device Manager to manage AVDs.
 */
public class AvdManager {

    private File mBaseAvdFolder;

    /**
     * Exception thrown when something is wrong with a target path.
     */
    private static final class InvalidTargetPathException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidTargetPathException(String message) {
            super(message);
        }
    }

    private static final Pattern INI_LINE_PATTERN =
        Pattern.compile("^([a-zA-Z0-9._-]+)\\s*=\\s*(.*)\\s*$");        //$NON-NLS-1$

    public static final String AVD_FOLDER_EXTENSION = ".avd";           //$NON-NLS-1$

    /** Charset encoding used by the avd.ini/config.ini. */
    public static final String AVD_INI_ENCODING = "avd.ini.encoding";   //$NON-NLS-1$

    /**
     * The *absolute* path to the AVD folder (which contains the #CONFIG_INI file).
     */
    public static final String AVD_INFO_ABS_PATH = "path";              //$NON-NLS-1$

    /**
     * The path to the AVD folder (which contains the #CONFIG_INI file) relative to
     * the {@link AndroidLocation#FOLDER_DOT_ANDROID}. This information is written
     * in the avd ini <b>only</b> if the AVD folder is located under the .android path
     * (that is the relative that has no backward {@code ..} references).
     */
    public static final String AVD_INFO_REL_PATH = "path.rel";          //$NON-NLS-1$

    /**
     * The {@link IAndroidTarget#hashString()} of the AVD.
     */
    public static final String AVD_INFO_TARGET = "target";     //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the tag id of the specific avd
     */
    public static final String AVD_INI_TAG_ID = "tag.id"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the tag display of the specific avd
     */
    public static final String AVD_INI_TAG_DISPLAY = "tag.display"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the abi type of the specific avd
     */
    public static final String AVD_INI_ABI_TYPE = "abi.type"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the CPU architecture of the specific avd
     */
    public static final String AVD_INI_CPU_ARCH = "hw.cpu.arch"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the CPU architecture of the specific avd
     */
    public static final String AVD_INI_CPU_MODEL = "hw.cpu.model"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the number of processors to emulate when SMP is supported.
     */
    public static final String AVD_INI_CPU_CORES = "hw.cpu.ncore"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the manufacturer of the device this avd was based on.
     */
    public static final String AVD_INI_DEVICE_MANUFACTURER = "hw.device.manufacturer"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the name of the device this avd was based on.
     */
    public static final String AVD_INI_DEVICE_NAME = "hw.device.name"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the SDK-relative path of the skin folder, if any,
     * or a 320x480 like constant for a numeric skin size.
     *
     * @see #NUMERIC_SKIN_SIZE
     */
    public static final String AVD_INI_SKIN_PATH = "skin.path"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the SDK-relative path of the skin folder to be selected if
     * skins for this device become enabled.
     */
    public static final String AVD_INI_BACKUP_SKIN_PATH = "skin.path.backup"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing an UI name for the skin.
     * This config key is ignored by the emulator. It is only used by the SDK manager or
     * tools to give a friendlier name to the skin.
     * If missing, use the {@link #AVD_INI_SKIN_PATH} key instead.
     */
    public static final String AVD_INI_SKIN_NAME = "skin.name"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing whether a dynamic skin should be displayed.
     */
    public static final String AVD_INI_SKIN_DYNAMIC = "skin.dynamic"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the path to the sdcard file.
     * If missing, the default name "sdcard.img" will be used for the sdcard, if there's such
     * a file.
     *
     * @see #SDCARD_IMG
     */
    public static final String AVD_INI_SDCARD_PATH = "sdcard.path"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the size of the SD card.
     * This property is for UI purposes only. It is not used by the emulator.
     *
     * @see #SDCARD_SIZE_PATTERN
     * @see #parseSdcardSize(String, String[])
     */
    public static final String AVD_INI_SDCARD_SIZE = "sdcard.size"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the first path where the emulator looks
     * for system images. Typically this is the path to the add-on system image or
     * the path to the platform system image if there's no add-on.
     * <p>
     * The emulator looks at {@link #AVD_INI_IMAGES_1} before {@link #AVD_INI_IMAGES_2}.
     */
    public static final String AVD_INI_IMAGES_1 = "image.sysdir.1"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the second path where the emulator looks
     * for system images. Typically this is the path to the platform system image.
     *
     * @see #AVD_INI_IMAGES_1
     */
    public static final String AVD_INI_IMAGES_2 = "image.sysdir.2"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the presence of the snapshots file.
     * This property is for UI purposes only. It is not used by the emulator.
     *
     * @see #SNAPSHOTS_IMG
     */
    public static final String AVD_INI_SNAPSHOT_PRESENT = "snapshot.present"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing whether hardware OpenGLES emulation is enabled
     */
    public static final String AVD_INI_GPU_EMULATION = "hw.gpu.enabled"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing which software OpenGLES should be used
     */
    public static final String AVD_INI_GPU_MODE = "hw.gpu.mode";

    /**
     * AVD/config.ini key name representing how to emulate the front facing camera
     */
    public static final String AVD_INI_CAMERA_FRONT = "hw.camera.front"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing how to emulate the rear facing camera
     */
    public static final String AVD_INI_CAMERA_BACK = "hw.camera.back"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the amount of RAM the emulated device should have
     */
    public static final String AVD_INI_RAM_SIZE = "hw.ramSize";

    /**
     * AVD/config.ini key name representing the amount of memory available to applications by default
     */
    public static final String AVD_INI_VM_HEAP_SIZE = "vm.heapSize";

    /**
     * AVD/config.ini key name representing the size of the data partition
     */
    public static final String AVD_INI_DATA_PARTITION_SIZE = "disk.dataPartition.size";

    /**
     * AVD/config.ini key name representing the hash of the device this AVD is based on. <br>
     * This old hash is deprecated and shouldn't be used anymore.
     * It represents the Device.hashCode() and is not stable accross implementations.
     * @see #AVD_INI_DEVICE_HASH_V2
     */
    public static final String AVD_INI_DEVICE_HASH_V1 = "hw.device.hash";

    /**
     * AVD/config.ini key name representing the hash of the device hardware properties
     * actually present in the config.ini. This replaces {@link #AVD_INI_DEVICE_HASH_V1}.
     * <p>
     * To find this hash, use
     * {@code DeviceManager.getHardwareProperties(device).get(AVD_INI_DEVICE_HASH_V2)}.
     */
    public static final String AVD_INI_DEVICE_HASH_V2 = "hw.device.hash2";

    /**
     * The API level of this AVD. Derived from the target hash.
     */
    public static final String AVD_INI_ANDROID_API = "image.androidVersion.api";

    /**
     * The API codename of this AVD. Derived from the target hash.
     */
    public static final String AVD_INI_ANDROID_CODENAME = "image.androidVersion.codename";

    /**
     * Pattern to match pixel-sized skin "names", e.g. "320x480".
     */
    public static final Pattern NUMERIC_SKIN_SIZE = Pattern.compile("([0-9]{2,})x([0-9]{2,})"); //$NON-NLS-1$

    private static final String USERDATA_IMG = "userdata.img"; //$NON-NLS-1$
    private static final String BOOT_PROP = "boot.prop"; //$NON-NLS-1$
    static final String CONFIG_INI = "config.ini"; //$NON-NLS-1$
    private static final String SDCARD_IMG = "sdcard.img"; //$NON-NLS-1$
    private static final String SNAPSHOTS_IMG = "snapshots.img"; //$NON-NLS-1$

    static final String INI_EXTENSION = ".ini"; //$NON-NLS-1$
    private static final Pattern INI_NAME_PATTERN = Pattern.compile("(.+)\\" + //$NON-NLS-1$
            INI_EXTENSION + "$",                                               //$NON-NLS-1$
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMAGE_NAME_PATTERN = Pattern.compile("(.+)\\.img$", //$NON-NLS-1$
            Pattern.CASE_INSENSITIVE);

    /**
     * Pattern for matching SD Card sizes, e.g. "4K" or "16M".
     * Callers should use {@link #parseSdcardSize(String, String[])} instead of using this directly.
     */
    private static final Pattern SDCARD_SIZE_PATTERN = Pattern.compile("(\\d+)([KMG])"); //$NON-NLS-1$

    /**
     * Minimal size of an SDCard image file in bytes. Currently 9 MiB.
     */

    public static final long SDCARD_MIN_BYTE_SIZE = 9<<20;
    /**
     * Maximal size of an SDCard image file in bytes. Currently 1023 GiB.
     */
    public static final long SDCARD_MAX_BYTE_SIZE = 1023L<<30;

    /** The sdcard string represents a valid number but the size is outside of the allowed range. */
    public static final int SDCARD_SIZE_NOT_IN_RANGE = 0;
    /** The sdcard string looks like a size number+suffix but the number failed to decode. */
    public static final int SDCARD_SIZE_INVALID = -1;
    /** The sdcard string doesn't look like a size, it might be a path instead. */
    public static final int SDCARD_NOT_SIZE_PATTERN = -2;

    public static final String HARDWARE_INI = "hardware.ini"; //$NON-NLS-1$

    // A map where the keys are the locations of the SDK and the values are the corresponding
    // AvdManagers. This prevents us from creating multiple AvdManagers for the same SDK and having
    // them get out of sync.
    private static final Table<String, FileOp, WeakReference<AvdManager>> mManagers =
        HashBasedTable.create();

    private final ArrayList<AvdInfo> mAllAvdList = new ArrayList<>();
    private AvdInfo[] mValidAvdList;
    private AvdInfo[] mBrokenAvdList;
    private final AndroidSdkHandler mSdkHandler;
    private final Map<ILogger, DeviceManager> mDeviceManagers =
            new HashMap<>();
    private final FileOp mFop;

    protected AvdManager(
            @NonNull AndroidSdkHandler sdkHandler,
            @NonNull File baseAvdFolder,
            @NonNull ILogger log,
            @NonNull FileOp fop)
            throws AndroidLocationException {
        mSdkHandler = sdkHandler;
        mFop = fop;
        mBaseAvdFolder = baseAvdFolder;
        buildAvdList(mAllAvdList, log);
    }

    /**
     * Returns an AVD Manager for a given SDK represented by {@code sdkHandler}.
     * One AVD Manager instance is created by SDK location and then cached and reused.
     *
     * @param sdkHandler The SDK handler.
     * @param log The log object to receive the log of the initial loading of the AVDs.
     *            This log object is not kept by this instance of AvdManager and each
     *            method takes its own logger. The rationale is that the AvdManager
     *            might be called from a variety of context, each with different
     *            logging needs. Cannot be null.
     * @return The AVD Manager instance.
     * @throws AndroidLocationException if {@code sdkHandler} does not have a local path set.
     */
    @Nullable
    public static AvdManager getInstance(@NonNull AndroidSdkHandler sdkHandler,
            @NonNull ILogger log)
            throws AndroidLocationException {
        return getInstance(
                sdkHandler,
                new File(AndroidLocation.getAvdFolder()),
                log,
                FileOpUtils.create());
    }

    @Nullable
    public static AvdManager getInstance(
            @NonNull AndroidSdkHandler sdkHandler,
            @NonNull File baseAvdFolder,
            @NonNull ILogger log,
            @NonNull FileOp fop)
            throws AndroidLocationException {
        if (sdkHandler.getLocation() == null) {
            throw new AndroidLocationException("Local SDK path not set!");
        }
        synchronized(mManagers) {
            AvdManager manager;
            WeakReference<AvdManager> ref = mManagers
                    .get(sdkHandler.getLocation().getPath(), fop);
            if (ref != null && (manager = ref.get()) != null) {
                return manager;
            }
            try {
                manager = new AvdManager(sdkHandler, baseAvdFolder, log, fop);
            }
            catch (AndroidLocationException e){
                throw e;
            }
            catch (Exception e) {
                log.warning("Exception during AvdManager initialization: %1$s", e);
                return null;
            }
            mManagers.put(sdkHandler.getLocation().getPath(), fop,
                    new WeakReference<>(manager));
            return manager;
        }
    }

    /**
     * Returns the base folder where AVDs are created.
     *
     * @throws AndroidLocationException
     */
    @NonNull
    public File getBaseAvdFolder() throws AndroidLocationException {
        return mBaseAvdFolder;
    }

    /**
     * Parse the sdcard string to decode the size.
     * Returns:
     * <ul>
     * <li> The size in bytes > 0 if the sdcard string is a valid size in the allowed range.
     * <li> {@link #SDCARD_SIZE_NOT_IN_RANGE} (0)
     *          if the sdcard string is a valid size NOT in the allowed range.
     * <li> {@link #SDCARD_SIZE_INVALID} (-1)
     *          if the sdcard string is number that fails to parse correctly.
     * <li> {@link #SDCARD_NOT_SIZE_PATTERN} (-2)
     *          if the sdcard string is not a number, in which case it's probably a file path.
     * </ul>
     *
     * @param sdcard The sdcard string, which can be a file path, a size string or something else.
     * @param parsedStrings If non-null, an array of 2 strings. The first string will be
     *  filled with the parsed numeric size and the second one will be filled with the
     *  parsed suffix. This is filled even if the returned size is deemed out of range or
     *  failed to parse. The values are null if the sdcard is not a size pattern.
     * @return A size in byte if > 0, or {@link #SDCARD_SIZE_NOT_IN_RANGE},
     *  {@link #SDCARD_SIZE_INVALID} or {@link #SDCARD_NOT_SIZE_PATTERN} as error codes.
     */
    public static long parseSdcardSize(@NonNull String sdcard, @Nullable String[] parsedStrings) {

        if (parsedStrings != null) {
            assert parsedStrings.length == 2;
            parsedStrings[0] = null;
            parsedStrings[1] = null;
        }

        Matcher m = SDCARD_SIZE_PATTERN.matcher(sdcard);
        if (m.matches()) {
            if (parsedStrings != null) {
                assert parsedStrings.length == 2;
                parsedStrings[0] = m.group(1);
                parsedStrings[1] = m.group(2);
            }

            // get the sdcard values for checks
            try {
                long sdcardSize = Long.parseLong(m.group(1));

                String sdcardSizeModifier = m.group(2);
                if ("K".equals(sdcardSizeModifier)) {           //$NON-NLS-1$
                    sdcardSize <<= 10;
                } else if ("M".equals(sdcardSizeModifier)) {    //$NON-NLS-1$
                    sdcardSize <<= 20;
                } else if ("G".equals(sdcardSizeModifier)) {    //$NON-NLS-1$
                    sdcardSize <<= 30;
                }

                if (sdcardSize < SDCARD_MIN_BYTE_SIZE ||
                        sdcardSize > SDCARD_MAX_BYTE_SIZE) {
                    return SDCARD_SIZE_NOT_IN_RANGE;
                }

                return sdcardSize;
            } catch (NumberFormatException e) {
                // This could happen if the number is too large to fit in a long.
                return SDCARD_SIZE_INVALID;
            }
        }

        return SDCARD_NOT_SIZE_PATTERN;
    }

    /**
     * Returns all the existing AVDs.
     * @return a newly allocated array containing all the AVDs.
     */
    @NonNull
    public AvdInfo[] getAllAvds() {
        synchronized (mAllAvdList) {
            return mAllAvdList.toArray(new AvdInfo[mAllAvdList.size()]);
        }
    }

    /**
     * Returns all the valid AVDs.
     * @return a newly allocated array containing all valid the AVDs.
     */
    @NonNull
    public AvdInfo[] getValidAvds() {
        synchronized (mAllAvdList) {
            if (mValidAvdList == null) {
                ArrayList<AvdInfo> list = new ArrayList<>();
                for (AvdInfo avd : mAllAvdList) {
                    if (avd.getStatus() == AvdStatus.OK) {
                        list.add(avd);
                    }
                }

                mValidAvdList = list.toArray(new AvdInfo[list.size()]);
            }
            return mValidAvdList;
        }
    }

    /**
     * Returns the {@link AvdInfo} matching the given <var>name</var>.
     * <p>
     * The search is case-insensitive.
     *
     * @param name the name of the AVD to return
     * @param validAvdOnly if <code>true</code>, only look through the list of valid AVDs.
     * @return the matching AvdInfo or <code>null</code> if none were found.
     */
    @Nullable
    public AvdInfo getAvd(@Nullable String name, boolean validAvdOnly) {

        boolean ignoreCase = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;

        if (validAvdOnly) {
            for (AvdInfo info : getValidAvds()) {
                String name2 = info.getName();
                if (name2.equals(name) || (ignoreCase && name2.equalsIgnoreCase(name))) {
                    return info;
                }
            }
        } else {
            synchronized (mAllAvdList) {
                for (AvdInfo info : mAllAvdList) {
                    String name2 = info.getName();
                    if (name2.equals(name) || (ignoreCase && name2.equalsIgnoreCase(name))) {
                        return info;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns whether an emulator is currently running the AVD.
     */
    public boolean isAvdRunning(@NonNull AvdInfo info, @NonNull ILogger logger) {
        String pid;
        try {
            pid = getAvdPid(info);
        }
        catch (IOException e) {
            logger.error(e, "IOException while getting PID");
            // To be safe return true
            return true;
        }
        if (pid != null) {
            String command;
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                command = "cmd /c \"tasklist /FI \"PID eq " + pid + "\" | findstr " + pid
                        + "\"";
            } else {
                command = "kill -0 " + pid;
            }
            try {
                Process p = Runtime.getRuntime().exec(command);
                // If the process ends with non-0 it means the process doesn't exist
                return p.waitFor() == 0;
            } catch (IOException e) {
                logger.warning("Got IOException while checking running processes:\n%s",
                        Arrays.toString(e.getStackTrace()));
                // To be safe return true
                return true;
            } catch (InterruptedException e) {
                logger.warning("Got InterruptedException while checking running processes:\n%s",
                        Arrays.toString(e.getStackTrace()));
                // To be safe return true
                return true;
            }
        }

        return false;
    }

    public void stopAvd(@NonNull AvdInfo info) {
        try {
            String pid = getAvdPid(info);
            if (pid != null) {
                String command;
                if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                    command = "cmd /c \"taskkill /PID " + pid + "\"";
                } else {
                    command = "kill " + pid;
                }
                try {
                    Process p = Runtime.getRuntime().exec(command);
                    // If the process ends with non-0 it means the process doesn't exist
                    p.waitFor();
                } catch (InterruptedException e) {
                }
            }
        }
        catch (IOException e) {
        }
    }

    private String getAvdPid(@NonNull AvdInfo info) throws IOException {
        // this is a file on Unix, and a directory on Windows.
        File f = new File(info.getDataFolderPath(), "hardware-qemu.ini.lock");   //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            f = new File(f, "pid");
        }
        // This is an alternative identifier for Unix and Windows when the above one is missing.
        File alternative = new File(info.getDataFolderPath(), "userdata-qemu.img.lock");
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            alternative = new File(alternative, "pid");
        }
        if (mFop.exists(f)) {
            return mFop.toString(f, Charsets.UTF_8).trim();
        }
        if (mFop.exists(alternative)){
            return mFop.toString(alternative, Charsets.UTF_8).trim();
        }
        return null;
    }

    /**
     * Reloads the AVD list.
     * @param log the log object to receive action logs. Cannot be null.
     * @throws AndroidLocationException if there was an error finding the location of the
     * AVD folder.
     */
    public void reloadAvds(@NonNull ILogger log) throws AndroidLocationException {
        // build the list in a temp list first, in case the method throws an exception.
        // It's better than deleting the whole list before reading the new one.
        ArrayList<AvdInfo> allList = new ArrayList<>();
        buildAvdList(allList, log);

        synchronized (mAllAvdList) {
            mAllAvdList.clear();
            mAllAvdList.addAll(allList);
            mValidAvdList = mBrokenAvdList = null;
        }
    }

    /**
     * Reloads a single AVD but does not update the list.
     * @param avdInfo an existing AVD
     * @param log the log object to receive action logs. Cannot be null.
     * @return an updated AVD
     * @throws AndroidLocationException if there was an error finding the location of the
     * AVD folder.
     */
    public AvdInfo reloadAvd(AvdInfo avdInfo, ILogger log) throws AndroidLocationException {
        AvdInfo newInfo = parseAvdInfo(avdInfo.getIniFile(), log);
        synchronized (mAllAvdList) {
            int index = mAllAvdList.indexOf(avdInfo);
            if (index >= 0) {
                // Update the existing list of AVDs
                // Unless the original AVD is not found, in which case someone else may already have updated the list
                replaceAvd(avdInfo, newInfo);
            }
        }
        return newInfo;
    }

    /**
     * Creates a new AVD. It is expected that there is no existing AVD with this name already.
     *
     * @param avdFolder the data folder for the AVD. It will be created as needed.
     *   Unless you want to locate it in a specific directory, the ideal default is
     *   {@code AvdManager.AvdInfo.getAvdFolder}.
     * @param avdName the name of the AVD
     * @param systemImage the system image of the AVD
     * @param skinFolder the skin folder path to use, if specified. Can be null.
     * @param skinName the name of the skin. Can be null. Must have been verified by caller.
     *          Can be a size in the form "NNNxMMM" or a directory name matching skinFolder.
     * @param sdcard the parameter value for the sdCard. Can be null. This is either a path to
     *        an existing sdcard image or a sdcard size (\d+, \d+K, \dM).
     * @param hardwareConfig the hardware setup for the AVD. Can be null to use defaults.
     * @param bootProps the optional boot properties for the AVD. Can be null.
     * @param createSnapshot If true copy a blank snapshot image into the AVD.
     * @param removePrevious If true remove any previous files.
     * @param editExisting If true, edit an existing AVD, changing only the minimum required.
     *          This won't remove files unless required or unless {@code removePrevious} is set.
     * @param log the log object to receive action logs. Cannot be null.
     * @return The new {@link AvdInfo} in case of success (which has just been added to the
     *         internal list) or null in case of failure.
     */
    @Nullable
    public AvdInfo createAvd(
            @NonNull  File avdFolder,
            @NonNull  String avdName,
            @NonNull  ISystemImage systemImage,
            @Nullable File skinFolder,
            @Nullable String skinName,
            @Nullable String sdcard,
            @Nullable Map<String,String> hardwareConfig,
            @Nullable Map<String,String> bootProps,
            boolean createSnapshot,
            boolean removePrevious,
            boolean editExisting,
            @NonNull ILogger log) {
        if (log == null) {
            throw new IllegalArgumentException("log cannot be null");
        }

        File iniFile = null;
        boolean needCleanup = false;
        try {
            if (mFop.exists(avdFolder)) {
                if (removePrevious) {
                    // AVD already exists and removePrevious is set, try to remove the
                    // directory's content first (but not the directory itself).
                    try {
                        deleteContentOf(avdFolder);
                    } catch (SecurityException e) {
                        log.warning("Failed to delete %1$s: %2$s", avdFolder.getAbsolutePath(), e);
                    }
                } else if (!editExisting) {
                    // AVD shouldn't already exist if removePrevious is false and
                    // we're not editing an existing AVD.
                    log.warning("Folder %1$s is in the way. Move it away to continue.",
                            avdFolder.getAbsolutePath());
                    return null;
                }
            } else {
                // create the AVD folder.
                mFop.mkdirs(avdFolder);
                // We're not editing an existing AVD.
                editExisting = false;
            }

            // actually write the ini file
            iniFile = createAvdIniFile(avdName, avdFolder, removePrevious,
              systemImage.getAndroidVersion());

            // writes the userdata.img in it.
            File imageFolder = systemImage.getLocation();
            File userdataSrc = new File(imageFolder, USERDATA_IMG);

            String abiType = systemImage.getAbiType();

            if (!mFop.exists(userdataSrc)) {
                log.warning("Unable to find a '%1$s' file for ABI %2$s to copy into the AVD folder.",
                        USERDATA_IMG,
                        abiType);
                needCleanup = true;
                return null;
            }

            File userdataDest = new File(avdFolder, USERDATA_IMG);

            mFop.copyFile(userdataSrc, userdataDest);

            if (!mFop.exists(userdataDest)) {
                log.warning("Unable to create '%1$s' file in the AVD folder.",
                        userdataDest);
                needCleanup = true;
                return null;
            }

            // Config file.
            HashMap<String, String> values = new HashMap<>();

            if (!setImagePathProperties(systemImage, values, log)) {
               log.warning("Failed to set image path properties in the AVD folder.");
               needCleanup = true;
               return null;
            }

            // Create the snapshot file
            if (createSnapshot) {
                File snapshotDest = new File(avdFolder, SNAPSHOTS_IMG);
                if (mFop.isFile(snapshotDest) && editExisting) {
                    log.info("Snapshot image already present, was not changed.\n");

                } else {
                    File toolsLib = new File(mSdkHandler.getLocation(),
                                             SdkConstants.OS_SDK_TOOLS_LIB_EMULATOR_FOLDER);
                    File snapshotBlank = new File(toolsLib, SNAPSHOTS_IMG);
                    if (!mFop.exists(snapshotBlank)) {
                        log.warning(
                                "Unable to find a '%2$s%1$s' file to copy into the AVD folder.",
                                SNAPSHOTS_IMG, toolsLib);
                        needCleanup = true;
                        return null;
                    }

                    mFop.copyFile(snapshotBlank, snapshotDest);
                }
                values.put(AVD_INI_SNAPSHOT_PRESENT, "true");
            }

            // Now the tag &amp; abi type
            IdDisplay tag = systemImage.getTag();
            values.put(AVD_INI_TAG_ID,      tag.getId());
            values.put(AVD_INI_TAG_DISPLAY, tag.getDisplay());
            values.put(AVD_INI_ABI_TYPE,    abiType);

            // and the cpu arch.
            Abi abi = Abi.getEnum(abiType);
            if (abi != null) {
                values.put(AVD_INI_CPU_ARCH, abi.getCpuArch());

                String model = abi.getCpuModel();
                if (model != null) {
                    values.put(AVD_INI_CPU_MODEL, model);
                }
            } else {
                log.warning("ABI %1$s is not supported by this version of the SDK Tools", abiType);
                needCleanup = true;
                return null;
            }

            // Now the skin.
            String skinPath = null;

            if (skinFolder == null && skinName != null &&
                    NUMERIC_SKIN_SIZE.matcher(skinName).matches()) {
                // Numeric skin size. Set both skinPath and skinName to the same size.
                skinPath = skinName;

            } else if (skinFolder != null && skinName == null) {
                // Skin folder is specified, but not skin name. Adjust it.
                skinName = skinFolder.getName();

            }

            if (skinFolder != null) {
                // skin does not exist!
                if (!mFop.exists(skinFolder)) {
                    log.warning("Skin '%1$s' does not exist at %2$s.", skinName, skinFolder.getPath());
                    return null;
                }

                // if skinFolder is in the sdk, use the relative path
                if (skinFolder.getPath().startsWith(mSdkHandler.getLocation().getPath())) {
                    try {
                        skinPath = FileOpUtils.makeRelative(mSdkHandler.getLocation(), skinFolder,
                                mFop);
                    } catch (IOException e) {
                        // In case it fails, just use the absolute path
                        skinPath = skinFolder.getAbsolutePath();
                    }
                } else {
                    // Skin isn't in the sdk. Just use the absolute path.
                    skinPath = skinFolder.getAbsolutePath();
                }
            }

            // Set skin.name for display purposes in the AVD manager and
            // set skin.path for use by the emulator.
            if (skinName != null) {
                values.put(AVD_INI_SKIN_NAME, skinName);
            }
            if (skinPath != null) {
                values.put(AVD_INI_SKIN_PATH, skinPath);
            }

            if (sdcard != null && !sdcard.isEmpty()) {
                // Sdcard is possibly a size. In that case we create a file called 'sdcard.img'
                // in the AVD folder, and do not put any value in config.ini.

                long sdcardSize = parseSdcardSize(sdcard, null);

                if (sdcardSize == SDCARD_SIZE_NOT_IN_RANGE) {
                    log.warning("SD Card size must be in the range 9 MiB..1023 GiB.");
                    needCleanup = true;
                    return null;

                } else if (sdcardSize == SDCARD_SIZE_INVALID) {
                    log.warning("Unable to parse SD Card size");
                    needCleanup = true;
                    return null;

                } else if (sdcardSize == SDCARD_NOT_SIZE_PATTERN) {
                    File sdcardFile = new File(sdcard);
                    if (mFop.isFile(sdcardFile)) {
                        // sdcard value is an external sdcard, so we put its path into the config.ini
                        values.put(AVD_INI_SDCARD_PATH, sdcard);
                    } else {
                        log.warning("'%1$s' is not recognized as a valid sdcard value.\n"
                                + "Value should be:\n" + "1. path to an sdcard.\n"
                                + "2. size of the sdcard to create: <size>[K|M]", sdcard);
                        needCleanup = true;
                        return null;
                    }
                } else {
                    // create the sdcard.
                    File sdcardFile = new File(avdFolder, SDCARD_IMG);

                    boolean runMkSdcard = true;
                    if (mFop.exists(sdcardFile)) {
                        if (sdcardFile.length() == sdcardSize && editExisting) {
                            // There's already an sdcard file with the right size and we're
                            // not overriding it... so don't remove it.
                            runMkSdcard = false;
                            log.info("SD Card already present with same size, was not changed.\n");
                        }
                    }
                    if (mFop instanceof MockFileOp) {
                        // We don't have a real filesystem, so we won't be able to run the tool. Skip.
                        runMkSdcard = false;
                    }

                    if (runMkSdcard) {
                        String path = sdcardFile.getAbsolutePath();

                        // execute mksdcard with the proper parameters.
                        LoggerProgressIndicatorWrapper progress = new LoggerProgressIndicatorWrapper(log);
                        LocalPackage p = mSdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, progress);
                        if (p == null) {
                            p = mSdkHandler.getLocalPackage(SdkConstants.FD_TOOLS, progress);
                        }
                        if (p == null) {
                            progress.logWarning(String.format(
                                    "Unable to find %1$s in the %2$s or %3$s components",
                                    SdkConstants.mkSdCardCmdName(), SdkConstants.FD_EMULATOR,
                                    SdkConstants.FD_TOOLS));
                            needCleanup = true;
                            return null;
                        }
                        File mkSdCard = new File(p.getLocation(), SdkConstants.mkSdCardCmdName());

                        if (!mFop.isFile(mkSdCard)) {
                            log.warning("'%1$s' is missing from the SDK tools folder.",
                                    mkSdCard.getName());
                            needCleanup = true;
                            return null;
                        }

                        if (!createSdCard(mkSdCard.getAbsolutePath(), sdcard, path, log)) {
                            log.warning("Failed to create sdcard in the AVD folder.");
                            needCleanup = true;
                            return null; // mksdcard output has already been displayed, no need to
                                         // output anything else.
                        }
                    }

                    // add a property containing the size of the sdcard for display purpose
                    // only when the dev does 'android list avd'
                    values.put(AVD_INI_SDCARD_SIZE, sdcard);
                }
            }

            // add the hardware config to the config file.
            // priority order is:
            // - values provided by the user
            // - values provided by the skin
            // - values provided by the sys img
            // In order to follow this priority, we'll add the lowest priority values first and then
            // override by higher priority values.
            // In the case of a platform with override values from the user, the skin value might
            // already be there, but it's ok.

            HashMap<String, String> finalHardwareValues = new HashMap<>();

            FileOpFileWrapper sysImgHardwareFile =
              new FileOpFileWrapper(new File(systemImage.getLocation(), HARDWARE_INI),
                mFop, false);
            if (sysImgHardwareFile.exists()) {
                Map<String, String> imageHardwardConfig = ProjectProperties.parsePropertyFile(
                        sysImgHardwareFile, log);

                if (imageHardwardConfig != null) {
                    finalHardwareValues.putAll(imageHardwardConfig);
                    values.putAll(imageHardwardConfig);
                }
            }

            // get the hardware properties for this skin
            if (skinFolder != null) {
                FileOpFileWrapper skinHardwareFile = new FileOpFileWrapper(
                  new File(skinFolder, HARDWARE_INI), mFop, false);
                if (skinHardwareFile.exists()) {
                    Map<String, String> skinHardwareConfig =
                        ProjectProperties.parsePropertyFile(skinHardwareFile, log);

                    if (skinHardwareConfig != null) {
                        finalHardwareValues.putAll(skinHardwareConfig);
                        values.putAll(skinHardwareConfig);
                    }
                }
            }

            // finally put the hardware provided by the user.
            if (hardwareConfig != null) {
                finalHardwareValues.putAll(hardwareConfig);
                values.putAll(hardwareConfig);
            }

            File configIniFile = new File(avdFolder, CONFIG_INI);
            writeIniFile(configIniFile, values, true);

            if (bootProps != null && !bootProps.isEmpty()) {
                File bootPropsFile = new File(avdFolder, BOOT_PROP);
                writeIniFile(bootPropsFile, bootProps, false);
            }

            // create the AvdInfo object, and add it to the list
            AvdInfo newAvdInfo = new AvdInfo(
                    avdName,
                    iniFile,
                    avdFolder.getAbsolutePath(),
                    systemImage,
                    values);

            AvdInfo oldAvdInfo = getAvd(avdName, false /*validAvdOnly*/);

            synchronized (mAllAvdList) {
                if (oldAvdInfo != null && (removePrevious || editExisting)) {
                    mAllAvdList.remove(oldAvdInfo);
                }
                mAllAvdList.add(newAvdInfo);
                mValidAvdList = mBrokenAvdList = null;
            }

            if ((removePrevious || editExisting) &&
                    newAvdInfo != null &&
                    oldAvdInfo != null &&
                    !oldAvdInfo.getDataFolderPath().equals(newAvdInfo.getDataFolderPath())) {
                log.warning("Removing previous AVD directory at %s",
                        oldAvdInfo.getDataFolderPath());
                // Remove the old data directory
                File dir = new File(oldAvdInfo.getDataFolderPath());
                try {
                    deleteContentOf(dir);
                    mFop.delete(dir);
                } catch (SecurityException e) {
                    log.warning("Failed to delete %1$s: %2$s", dir.getAbsolutePath(), e);
                }
            }

            return newAvdInfo;
        } catch (SecurityException | AndroidLocationException | IOException e) {
            log.warning("%1$s", e);
        } finally {
            if (needCleanup) {
                if (iniFile != null && mFop.exists(iniFile)) {
                    mFop.delete(iniFile);
                }

                try {
                    deleteContentOf(avdFolder);
                    mFop.delete(avdFolder);
                } catch (SecurityException e) {
                    log.warning("Failed to delete %1$s: %2$s", avdFolder.getAbsolutePath(), e);
                }
            }
        }

        return null;
    }

    /**
     * Returns the path to the target images folder as a relative path to the SDK, if the folder
     * is not empty. If the image folder is empty or does not exist, <code>null</code> is returned.
     * @throws InvalidTargetPathException if the target image folder is not in the current SDK.
     */
    private String getImageRelativePath(@NonNull ISystemImage systemImage)
            throws InvalidTargetPathException {

        File folder = systemImage.getLocation();
        String imageFullPath = folder.getAbsolutePath();

        // make this path relative to the SDK location
        String sdkLocation = mSdkHandler.getLocation().getAbsolutePath();
        if (!imageFullPath.startsWith(sdkLocation)) {
            // this really really should not happen.
            assert false;
            throw new InvalidTargetPathException("Target location is not inside the SDK.");
        }

        if (mFop.isDirectory(folder)) {
            String[] list = mFop.list(folder,
                    (dir, name) -> IMAGE_NAME_PATTERN.matcher(name).matches());

            if (list.length > 0) {
                // Remove the SDK root path, e.g. /sdk/dir1/dir2 ⇒ /dir1/dir2
                imageFullPath = imageFullPath.substring(sdkLocation.length());
                // The path is relative, so it must not start with a file separator
                if (imageFullPath.charAt(0) == File.separatorChar) {
                    imageFullPath = imageFullPath.substring(1);
                }
                // For compatibility with previous versions, we denote folders
                // by ending the path with file separator
                if (!imageFullPath.endsWith(File.separator)) {
                    imageFullPath += File.separator;
                }

                return imageFullPath;
            }
        }

        return null;
    }

    /**
     * Creates the ini file for an AVD.
     *
     * @param name of the AVD.
     * @param avdFolder path for the data folder of the AVD.
     * @param removePrevious True if an existing ini file should be removed.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     * @throws IOException if {@link File#getAbsolutePath()} fails.
     */
    private File createAvdIniFile(@NonNull String name,
      @NonNull File avdFolder,
      boolean removePrevious,
      @NonNull AndroidVersion version)
      throws AndroidLocationException, IOException {
        File iniFile = AvdInfo.getDefaultIniFile(this, name);

        if (removePrevious) {
            if (mFop.isFile(iniFile)) {
                mFop.delete(iniFile);
            } else if (mFop.isDirectory(iniFile)) {
                deleteContentOf(iniFile);
                mFop.delete(iniFile);
            }
        }

        String absPath = avdFolder.getAbsolutePath();
        String relPath = null;
        File androidFolder = mSdkHandler.getAndroidFolder();
        if (androidFolder == null) {
            throw new AndroidLocation.AndroidLocationException(
                    "Can't locate ANDROID_HOME for the AVD .ini file.");
        }
        String androidPath = androidFolder.getAbsolutePath() + File.separator;
        if (absPath.startsWith(androidPath)) {
            // Compute the AVD path relative to the android path.
            assert androidPath.endsWith(File.separator);
            relPath = absPath.substring(androidPath.length());
        }

        HashMap<String, String> values = new HashMap<>();
        if (relPath != null) {
            values.put(AVD_INFO_REL_PATH, relPath);
        }
        values.put(AVD_INFO_ABS_PATH, absPath);
        values.put(AVD_INFO_TARGET, AndroidTargetHash.getPlatformHashString(version));
        writeIniFile(iniFile, values, true);

        return iniFile;
    }

    /**
     * Creates the ini file for an AVD.
     *
     * @param info of the AVD.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     * @throws IOException if {@link File#getAbsolutePath()} fails.
     */
    private File createAvdIniFile(@NonNull AvdInfo info)
            throws AndroidLocationException, IOException {
        return createAvdIniFile(info.getName(),
          new File(info.getDataFolderPath()),
          false, info.getAndroidVersion());
    }

    /**
     * Actually deletes the files of an existing AVD.
     * <p>
     * This also remove it from the manager's list, The caller does not need to
     * call {@link #removeAvd(AvdInfo)} afterwards.
     * <p>
     * This method is designed to somehow work with an unavailable AVD, that is an AVD that
     * could not be loaded due to some error. That means this method still tries to remove
     * the AVD ini file or its folder if it can be found. An error will be output if any of
     * these operations fail.
     *
     * @param avdInfo the information on the AVD to delete
     * @param log the log object to receive action logs. Cannot be null.
     * @return True if the AVD was deleted with no error.
     */
    public boolean deleteAvd(@NonNull AvdInfo avdInfo, @NonNull ILogger log) {
        try {
            boolean error = false;

            File f = avdInfo.getIniFile();
            if (f != null && mFop.exists(f)) {
                log.info("Deleting file %1$s\n", f.getCanonicalPath());
                if (!mFop.delete(f)) {
                    log.warning("Failed to delete %1$s\n", f.getCanonicalPath());
                    error = true;
                }
            }

            String path = avdInfo.getDataFolderPath();
            if (path != null) {
                f = new File(path);
                if (mFop.exists(f)) {
                    log.info("Deleting folder %1$s\n", f.getCanonicalPath());
                    if (deleteContentOf(f) == false || mFop.delete(f) == false) {
                        log.warning("Failed to delete %1$s\n", f.getCanonicalPath());
                        error = true;
                    }
                }
            }

            removeAvd(avdInfo);

            if (error) {
                log.info("\nAVD '%1$s' deleted with errors. See errors above.\n",
                        avdInfo.getName());
            } else {
                log.info("\nAVD '%1$s' deleted.\n", avdInfo.getName());
                return true;
            }

        } catch (IOException | SecurityException e) {
            log.warning("%1$s", e);
        }
        return false;
    }

    /**
     * Moves and/or rename an existing AVD and its files.
     * This also change it in the manager's list.
     * <p>
     * The caller should make sure the name or path given are valid, do not exist and are
     * actually different than current values.
     *
     * @param avdInfo the information on the AVD to move.
     * @param newName the new name of the AVD if non null.
     * @param paramFolderPath the new data folder if non null.
     * @param log the log object to receive action logs. Cannot be null.
     * @return True if the move succeeded or there was nothing to do.
     *         If false, this method will have had already output error in the log.
     */
    public boolean moveAvd(@NonNull  AvdInfo avdInfo,
                           @Nullable String newName,
                           @Nullable String paramFolderPath,
                           @NonNull  ILogger log) {

        try {
            if (paramFolderPath != null) {
                File f = new File(avdInfo.getDataFolderPath());
                log.info("Moving '%1$s' to '%2$s'.", avdInfo.getDataFolderPath(), paramFolderPath);
                if (!mFop.renameTo(f, new File(paramFolderPath))) {
                    log.warning(null, "Failed to move '%1$s' to '%2$s'.",
                            avdInfo.getDataFolderPath(), paramFolderPath);
                    return false;
                }

                // update AVD info
                AvdInfo info = new AvdInfo(
                        avdInfo.getName(),
                        avdInfo.getIniFile(),
                        paramFolderPath,
                        avdInfo.getSystemImage(),
                        avdInfo.getProperties());
                replaceAvd(avdInfo, info);

                // update the ini file
                createAvdIniFile(info);
            }

            if (newName != null) {
                File oldIniFile = avdInfo.getIniFile();
                File newIniFile = AvdInfo.getDefaultIniFile(this, newName);

                log.warning("Moving '%1$s' to '%2$s'.", oldIniFile.getPath(), newIniFile.getPath());
                if (!mFop.renameTo(oldIniFile, newIniFile)) {
                    log.warning(null, "Failed to move '%1$s' to '%2$s'.",
                            oldIniFile.getPath(), newIniFile.getPath());
                    return false;
                }

                // update AVD info
                AvdInfo info = new AvdInfo(
                        newName,
                        avdInfo.getIniFile(),
                        avdInfo.getDataFolderPath(),
                        avdInfo.getSystemImage(),
                        avdInfo.getProperties());
                replaceAvd(avdInfo, info);
            }

            log.info("AVD '%1$s' moved.\n", avdInfo.getName());

        } catch (AndroidLocationException | IOException e) {
            log.warning("$1%s", e);
            return false;
        }

        // nothing to do or succeeded
        return true;
    }

    /**
     * Helper method to recursively delete a folder's content (but not the folder itself).
     *
     * @throws SecurityException like {@link File#delete()} does if file/folder is not writable.
     */
    private boolean deleteContentOf(File folder) throws SecurityException {
        File[] files = mFop.listFiles(folder);
        if (files != null) {
            for (File f : files) {
                if (mFop.isDirectory(f)) {
                    if (deleteContentOf(f) == false) {
                        return false;
                    }
                }
                if (mFop.delete(f) == false) {
                    return false;
                }

            }
        }

        return true;
    }

    /**
     * Returns a list of files that are potential AVD ini files.
     * <p>
     * This lists the $HOME/.android/avd/<name>.ini files.
     * Such files are properties file than then indicate where the AVD folder is located.
     * <p>
     * Note: the method is to be considered private. It is made protected so that
     * unit tests can easily override the AVD root.
     *
     * @return A new {@link File} array or null. The array might be empty.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     */
    private File[] buildAvdFilesList() throws AndroidLocationException {
        // ensure folder validity.
        if (mFop.isFile(mBaseAvdFolder)) {
            throw new AndroidLocationException(
                    String.format("%1$s is not a valid folder.", mBaseAvdFolder.getAbsolutePath()));
        } else if (mFop.exists(mBaseAvdFolder) == false) {
            // folder is not there, we create it and return
            mFop.mkdirs(mBaseAvdFolder);
            return null;
        }

        File[] avds = mFop.listFiles(mBaseAvdFolder, (parent, name) -> {
            if (INI_NAME_PATTERN.matcher(name).matches()) {
                // check it's a file and not a folder
                return mFop.isFile(new File(parent, name));
            }

            return false;
        });

        return avds;
    }

    /**
     * Computes the internal list of available AVDs
     * @param allList the list to contain all the AVDs
     * @param log the log object to receive action logs. Cannot be null.
     *
     * @throws AndroidLocationException if there's a problem getting android root directory.
     */
    private void buildAvdList(ArrayList<AvdInfo> allList, ILogger log)
            throws AndroidLocationException {
        File[] avds = buildAvdFilesList();
        if (avds != null) {
            for (File avd : avds) {
                AvdInfo info = parseAvdInfo(avd, log);
                if (info != null && !allList.contains(info)) {
                    allList.add(info);
                }
            }
        }
    }

    private DeviceManager getDeviceManager(ILogger logger) {
        DeviceManager manager = mDeviceManagers.get(logger);
        if (manager == null) {
            manager =
                    DeviceManager.createInstance(
                            mSdkHandler.getLocation(),
                            mSdkHandler.getAndroidFolder(),
                            logger,
                            mFop);
            manager.registerListener(mDeviceManagers::clear);
            mDeviceManagers.put(logger, manager);
        }
        return manager;
    }

    /**
     * Parses an AVD .ini file to create an {@link AvdInfo}.
     *
     * @param iniPath The path to the AVD .ini file
     * @param log the log object to receive action logs. Cannot be null.
     * @return A new {@link AvdInfo} with an {@link AvdStatus} indicating whether this AVD is
     *         valid or not.
     */
    private AvdInfo parseAvdInfo(File iniPath, ILogger log) {
        Map<String, String> map = parseIniFile(
                new FileOpFileWrapper(iniPath, mFop, false),
                log);

        String avdPath = map.get(AVD_INFO_ABS_PATH);

        if (avdPath == null || !(mFop.isDirectory(new File(avdPath)))) {
            // Try to fallback on the relative path, if present.
            String relPath = map.get(AVD_INFO_REL_PATH);
            if (relPath != null) {
                File androidFolder = mSdkHandler.getAndroidFolder();
                File f = new File(androidFolder, relPath);
                if (mFop.isDirectory(f)) {
                    avdPath = f.getAbsolutePath();
                }
            }
        }

        FileOpFileWrapper configIniFile = null;
        Map<String, String> properties = null;
        LoggerProgressIndicatorWrapper progress = new LoggerProgressIndicatorWrapper(log);

        // load the AVD properties.
        if (avdPath != null) {
            configIniFile = new FileOpFileWrapper(new File(avdPath, CONFIG_INI), mFop, false);
        }

        if (configIniFile != null) {
            if (!configIniFile.exists()) {
                log.warning("Missing file '%1$s'.",  configIniFile.getOsLocation());
            } else {
                properties = parseIniFile(configIniFile, log);
            }
        }

        // get name
        String name = iniPath.getName();
        Matcher matcher = INI_NAME_PATTERN.matcher(iniPath.getName());
        if (matcher.matches()) {
            name = matcher.group(1);
        }

        // check the image.sysdir are valid
        boolean validImageSysdir = true;
        String imageSysDir = null;
        ISystemImage sysImage = null;
        if (properties != null) {
            imageSysDir = properties.get(AVD_INI_IMAGES_1);
            if (imageSysDir != null) {
                sysImage = mSdkHandler.getSystemImageManager(progress)
                  .getImageAt(new File(mSdkHandler.getLocation(), imageSysDir));
            }
        }


        // Get the device status if this AVD is associated with a device
        DeviceStatus deviceStatus = null;
        boolean updateHashV2 = false;
        if (properties != null) {
            String deviceName = properties.get(AVD_INI_DEVICE_NAME);
            String deviceMfctr = properties.get(AVD_INI_DEVICE_MANUFACTURER);

            Device d;

            if (deviceName != null && deviceMfctr != null) {
                DeviceManager devMan = getDeviceManager(log);
                d = devMan.getDevice(deviceName, deviceMfctr);
                deviceStatus = d == null ? DeviceStatus.MISSING : DeviceStatus.EXISTS;

                if (d != null) {
                    updateHashV2 = true;
                    String hashV2 = properties.get(AVD_INI_DEVICE_HASH_V2);
                    if (hashV2 != null) {
                        String newHashV2 = DeviceManager.hasHardwarePropHashChanged(d, hashV2);
                        if (newHashV2 == null) {
                            updateHashV2 = false;
                        } else {
                            properties.put(AVD_INI_DEVICE_HASH_V2, newHashV2);
                        }
                    }

                    String hashV1 = properties.get(AVD_INI_DEVICE_HASH_V1);
                    if (hashV1 != null) {
                        // will recompute a hash v2 and save it below
                        properties.remove(AVD_INI_DEVICE_HASH_V1);
                    }
                }
            }
        }


        // TODO: What about missing sdcard, skins, etc?

        AvdStatus status;

        if (avdPath == null) {
            status = AvdStatus.ERROR_PATH;
        } else if (configIniFile == null) {
            status = AvdStatus.ERROR_CONFIG;
        } else if (properties == null || imageSysDir == null) {
            status = AvdStatus.ERROR_PROPERTIES;
        } else if (!validImageSysdir) {
            status = AvdStatus.ERROR_IMAGE_DIR;
        } else if (deviceStatus == DeviceStatus.CHANGED) {
            status = AvdStatus.ERROR_DEVICE_CHANGED;
        } else if (deviceStatus == DeviceStatus.MISSING) {
            status = AvdStatus.ERROR_DEVICE_MISSING;
        } else if (sysImage == null) {
            status = AvdStatus.ERROR_IMAGE_MISSING;
        } else {
            status = AvdStatus.OK;
        }

        if (properties == null) {
            properties = Maps.newHashMap();
        }

        if (!properties.containsKey(AVD_INI_ANDROID_API) &&
            !properties.containsKey(AVD_INI_ANDROID_CODENAME)) {
            String targetHash = map.get(AVD_INFO_TARGET);
            AndroidVersion version = AndroidTargetHash.getVersionFromHash(targetHash);
            if (version != null) {
                properties.put(AVD_INI_ANDROID_API, Integer.toString(version.getApiLevel()));
                if (version.getCodename() != null) {
                    properties.put(AVD_INI_ANDROID_CODENAME, version.getCodename());
                }
            }
        }

        AvdInfo info = new AvdInfo(
                name,
                iniPath,
                avdPath,
                sysImage,
                properties,
                status);

        if (updateHashV2) {
            try {
                return updateDeviceChanged(info, log);
            } catch (IOException ignore) {}
        }

        return info;
    }

    /**
     * Writes a .ini file from a set of properties, using UTF-8 encoding.
     * The keys are sorted.
     * The file should be read back later by {@link #parseIniFile(IAbstractFile, ILogger)}.
     *
     * @param iniFile The file to generate.
     * @param values The properties to place in the ini file.
     * @param addEncoding When true, add a property {@link #AVD_INI_ENCODING} indicating the
     *                    encoding used to write the file.
     * @throws IOException if {@link FileWriter} fails to open, write or close the file.
     */
    private void writeIniFile(File iniFile, Map<String, String> values, boolean addEncoding)
            throws IOException {

        Charset charset = Charsets.UTF_8;
        try (OutputStreamWriter writer = new OutputStreamWriter(mFop.newFileOutputStream(iniFile),
                charset)){
            if (addEncoding) {
                // Write down the charset used in case we want to use it later.
                writer.write(String.format("%1$s=%2$s\n", AVD_INI_ENCODING, charset.name()));
            }

            ArrayList<String> keys = new ArrayList<>(values.keySet());
            // Do not save these values (always recompute)
            keys.remove(AVD_INI_ANDROID_API);
            keys.remove(AVD_INI_ANDROID_CODENAME);
            Collections.sort(keys);

            for (String key : keys) {
                String value = values.get(key);
                if (value != null) {
                    writer.write(String.format("%1$s=%2$s\n", key, value));
                }
            }
        }
    }

    /**
     * Parses a property file and returns a map of the content.
     * <p>
     * If the file is not present, null is returned with no error messages sent to the log.
     * <p>
     * Charset encoding will be either the system's default or the one specified by the
     * {@link #AVD_INI_ENCODING} key if present.
     *
     * @param propFile the property file to parse
     * @param log the ILogger object receiving warning/error from the parsing.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    public static Map<String, String> parseIniFile(
            @NonNull IAbstractFile propFile,
            @Nullable ILogger log) {
        return parseIniFileImpl(propFile, log, null);
    }

    /**
     * Implementation helper for the {@link #parseIniFile(IAbstractFile, ILogger)} method.
     * Don't call this one directly.
     *
     * @param propFile the property file to parse
     * @param log the ILogger object receiving warning/error from the parsing.
     * @param charset When a specific charset is specified, this will be used as-is.
     *   When null, the default charset will first be used and if the key
     *   {@link #AVD_INI_ENCODING} is found the parsing will restart using that specific
     *   charset.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    private static Map<String, String> parseIniFileImpl(
            @NonNull IAbstractFile propFile,
            @Nullable ILogger log,
            @Nullable Charset charset) {

        BufferedReader reader = null;
        try {
            boolean canChangeCharset = false;
            if (charset == null) {
                canChangeCharset = true;
                charset = Charsets.ISO_8859_1;
            }
            reader = new BufferedReader(new InputStreamReader(propFile.getContents(), charset));

            String line = null;
            Map<String, String> map = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.charAt(0) != '#') {

                    Matcher m = INI_LINE_PATTERN.matcher(line);
                    if (m.matches()) {
                        // Note: we do NOT escape values.
                        String key = m.group(1);
                        String value = m.group(2);

                        // If we find the charset encoding and it's not the same one and
                        // it's a valid one, re-read the file using that charset.
                        if (canChangeCharset &&
                                AVD_INI_ENCODING.equals(key) &&
                                !charset.name().equals(value) &&
                                Charset.isSupported(value)) {
                            charset = Charset.forName(value);
                            return parseIniFileImpl(propFile, log, charset);
                        }

                        map.put(key, value);
                    } else {
                        if (log != null) {
                            log.warning("Error parsing '%1$s': \"%2$s\" is not a valid syntax",
                                    propFile.getOsLocation(),
                                    line);
                        }
                        return null;
                    }
                }
            }

            return map;
        } catch (FileNotFoundException e) {
            // this should not happen since we usually test the file existence before
            // calling the method.
            // Return null below.
        } catch (IOException | StreamException e) {
            if (log != null) {
                log.warning("Error parsing '%1$s': %2$s.",
                        propFile.getOsLocation(),
                        e.getMessage());
            }
        } finally {
            try {
                Closeables.close(reader, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen.
            }
        }

        return null;
    }

    /**
     * Invokes the tool to create a new SD card image file.
     *
     * @param toolLocation The path to the mksdcard tool.
     * @param size The size of the new SD Card, compatible with {@link #SDCARD_SIZE_PATTERN}.
     * @param location The path of the new sdcard image file to generate.
     * @param log the log object to receive action logs. Cannot be null.
     * @return True if the sdcard could be created.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected boolean createSdCard(String toolLocation, String size, String location, ILogger log) {
        try {
            String[] command = new String[3];
            command[0] = toolLocation;
            command[1] = size;
            command[2] = location;
            Process process = Runtime.getRuntime().exec(command);

            final ArrayList<String> errorOutput = new ArrayList<>();
            final ArrayList<String> stdOutput = new ArrayList<>();

            int status = GrabProcessOutput.grabProcessOutput(
                    process,
                    Wait.WAIT_FOR_READERS,
                    new IProcessOutput() {
                        @Override
                        public void out(@Nullable String line) {
                            if (line != null) {
                                stdOutput.add(line);
                            }
                        }

                        @Override
                        public void err(@Nullable String line) {
                            if (line != null) {
                                errorOutput.add(line);
                            }
                        }
                    });

            if (status == 0) {
                return true;
            } else {
                for (String error : errorOutput) {
                    log.warning("%1$s", error);
                }
            }

        } catch (InterruptedException | IOException e) {
            // pass, print error below
        }

        log.warning("Failed to create the SD card.");
        return false;
    }

    /**
     * Removes an {@link AvdInfo} from the internal list.
     *
     * @param avdInfo The {@link AvdInfo} to remove.
     * @return true if this {@link AvdInfo} was present and has been removed.
     */
    public boolean removeAvd(AvdInfo avdInfo) {
        synchronized (mAllAvdList) {
            if (mAllAvdList.remove(avdInfo)) {
                mValidAvdList = mBrokenAvdList = null;
                return true;
            }
        }

        return false;
    }

    public AvdInfo updateAvd(AvdInfo avd,
            Map<String, String> newProperties) throws IOException {
        // now write the config file
        File configIniFile = new File(avd.getDataFolderPath(), CONFIG_INI);
        writeIniFile(configIniFile, newProperties, true);

        // finally create a new AvdInfo for this unbroken avd and add it to the list.
        // instead of creating the AvdInfo object directly we reparse it, to detect other possible
        // errors
        // FIXME: We may want to create this AvdInfo by reparsing the AVD instead. This could detect other errors.
        AvdInfo newAvd = new AvdInfo(
                avd.getName(),
                avd.getIniFile(),
                avd.getDataFolderPath(),
                avd.getSystemImage(),
                newProperties);

        replaceAvd(avd, newAvd);

        return newAvd;
    }

    /**
     * Updates the device-specific part of an AVD ini.
     * @param avd the AVD to update.
     * @param log the log object to receive action logs. Cannot be null.
     * @return The new AVD on success.
     */
    public AvdInfo updateDeviceChanged(AvdInfo avd, ILogger log) throws IOException {

        // Overwrite the properties derived from the device and nothing else
        Map<String, String> properties = new HashMap<>(avd.getProperties());

        DeviceManager devMan = getDeviceManager(log);
        Collection<Device> devices = devMan.getDevices(DeviceManager.ALL_DEVICES);
        String name = properties.get(AvdManager.AVD_INI_DEVICE_NAME);
        String manufacturer = properties.get(AvdManager.AVD_INI_DEVICE_MANUFACTURER);

        if (properties != null && devices != null && name != null && manufacturer != null) {
            for (Device d : devices) {
                if (d.getId().equals(name) && d.getManufacturer().equals(manufacturer)) {
                    properties.putAll(DeviceManager.getHardwareProperties(d));
                    try {
                        return updateAvd(avd, properties);
                    } catch (IOException e) {
                        log.warning("%1$s", e);
                    }
                }
            }
        } else {
            log.warning("Base device information incomplete or missing.");
        }
        return null;
    }

    /**
     * Sets the paths to the system images in a properties map.
     *
     * @param image the system image for this avd.
     * @param properties the properties in which to set the paths.
     * @param log the log object to receive action logs. Cannot be null.
     * @return true if success, false if some path are missing.
     */
    private boolean setImagePathProperties(ISystemImage image,
            Map<String, String> properties,
            ILogger log) {
        properties.remove(AVD_INI_IMAGES_1);
        properties.remove(AVD_INI_IMAGES_2);

        try {
            String property = AVD_INI_IMAGES_1;

            // First the image folders of the target itself
            String imagePath = getImageRelativePath(image);
            if (imagePath != null) {
                properties.put(property, imagePath);
                return true;
            }
        } catch (InvalidTargetPathException e) {
            log.warning("%1$s", e);
        }

        return false;
    }

    /**
     * Replaces an old {@link AvdInfo} with a new one in the lists storing them.
     * @param oldAvd the {@link AvdInfo} to remove.
     * @param newAvd the {@link AvdInfo} to add.
     */
    private void replaceAvd(AvdInfo oldAvd, AvdInfo newAvd) {
        synchronized (mAllAvdList) {
            mAllAvdList.remove(oldAvd);
            mAllAvdList.add(newAvd);
            mValidAvdList = mBrokenAvdList = null;
        }
    }

    /**
     * @deprecated This should only be used by swt code, and should be removed once swt is removed.
     */
    @Deprecated
    public AndroidSdkHandler getSdkHandler() {
        return mSdkHandler;
    }
}
