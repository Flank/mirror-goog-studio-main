/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.sdklib.tool;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.prefs.AndroidLocation;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdklib.util.CommandLineParser;
import com.android.utils.ILogger;
import com.android.utils.IReaderLogger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Specific command-line flags for the AVD Manager CLI
 */
class AvdManagerCli extends CommandLineParser {

    /*
     * Steps needed to add a new action:
     * - Each action is defined as a "verb object" followed by parameters.
     * - Either reuse a VERB_ constant or define a new one.
     * - Either reuse an OBJECT_ constant or define a new one.
     * - Add a new entry to mAction with a one-line help summary.
     * - In the constructor, add a define() call for each parameter (either mandatory
     *   or optional) for the given action.
     */

    private final static String VERB_LIST = "list";

    private final static String VERB_CREATE = "create";

    private final static String VERB_MOVE = "move";

    private final static String VERB_DELETE = "delete";

    private static final String OBJECT_AVDS = "avds";

    private static final String OBJECT_AVD = "avd";

    private static final String OBJECT_TARGET = "target";

    private static final String OBJECT_TARGETS = "targets";

    private static final String OBJECT_DEVICE = "device";

    private static final String OBJECT_DEVICES = "devices";

    private static final String KEY_IMAGE_PACKAGE = "package";

    private static final String KEY_NAME = "name";

    private static final String KEY_PATH = "path";

    private static final String KEY_SDCARD = "sdcard";

    private static final String KEY_FORCE = "force";

    private static final String KEY_RENAME = "rename";

    private static final String KEY_SNAPSHOT = "snapshot";

    private static final String KEY_COMPACT = "compact";

    private static final String KEY_EOL_NULL = "null";

    private static final String KEY_TAG = "tag";

    private static final String KEY_ABI = "abi";

    private static final String KEY_CLEAR_CACHE = "clear-cache";

    private static final String KEY_DEVICE = "device";

    /**
     * Java property that defines the location of the sdk/tools directory.
     */
    private final static String TOOLSDIR = "com.android.sdkmanager.toolsdir";

    /**
     * Java property that defines the working directory. On Windows the current working directory is
     * actually the tools dir, in which case this is used to get the original CWD.
     */
    private final static String WORKDIR = "com.android.sdkmanager.workdir";

    private final static String[] BOOLEAN_YES_REPLIES = new String[]{"yes", "y"};

    private final static String[] BOOLEAN_NO_REPLIES = new String[]{"no", "n"};

    /**
     * Regex used to validate characters that compose an AVD name.
     */
    private static final Pattern RE_AVD_NAME = Pattern.compile("[a-zA-Z0-9._-]+"); //$NON-NLS-1$

    /**
     * List of valid characters for an AVD name. Used for display purposes.
     */
    private static final String CHARS_AVD_NAME = "a-z A-Z 0-9 . _ -"; //$NON-NLS-1$

    /**
     * Path to the SDK folder. This is the parent of {@link #TOOLSDIR}.
     */
    private String mOsSdkFolder;

    /**
     * Logger object. Use this to print normal output, warnings or errors.
     */
    private ILogger mSdkLog;

    /**
     * The SDK manager parses the SDK folder and gives access to the content.
     */
    private AndroidSdkHandler mSdkHandler;

    private AvdManager mAvdManager;

    /**
     * Action definitions for AvdManager command line.
     * <p/>
     * This list serves two purposes: first it is used to know which verb/object actions are
     * acceptable on the command-line; second it provides a summary for each action that is printed
     * in the help.
     * <p/>
     * Each entry is a string array with: <ul> <li> the verb. <li> an object (use #NO_VERB_OBJECT if
     * there's no object). <li> a description. <li> an alternate form for the object (e.g. plural).
     * </ul>
     */
    private final static String[][] ACTIONS = {

            {VERB_LIST, NO_VERB_OBJECT,
                    "Lists existing targets or virtual devices."},
            {VERB_LIST, OBJECT_AVD,
                    "Lists existing Android Virtual Devices.",
                    OBJECT_AVDS},
            {VERB_LIST, OBJECT_TARGET,
                    "Lists existing targets.",
                    OBJECT_TARGETS},
            {VERB_LIST, OBJECT_DEVICE,
                    "Lists existing devices.",
                    OBJECT_DEVICES},
            {VERB_CREATE, OBJECT_AVD,
                    "Creates a new Android Virtual Device."},
            {VERB_MOVE, OBJECT_AVD,
                    "Moves or renames an Android Virtual Device."},
            {VERB_DELETE, OBJECT_AVD,
                    "Deletes an Android Virtual Device."},
    };

    public static void main(String[] args) {
        AtomicReference<AvdManagerCli> reference = new AtomicReference<>();
        ILogger logger = createLogger(reference);
        AvdManagerCli instance = new AvdManagerCli(logger);
        reference.set(instance);
        instance.run(args);
    }

    /**
     * Runs the sdk manager app
     */
    private void run(String[] args) {
        init();
        parseArgs(args);
        parseSdk();
        doAction();
    }

    /**
     * Creates the {@link #mSdkLog} object. This must be done before {@link #init()} as it will be
     * used to report errors. This logger prints to the attached console.
     */
    @NonNull
    private static ILogger createLogger(@NonNull AtomicReference<AvdManagerCli> cli) {
        return new IReaderLogger() {
            @Override
            public void error(Throwable t, String errorFormat, Object... args) {
                if (errorFormat != null) {
                    System.err.printf("Error: " + errorFormat, args);
                    if (!errorFormat.endsWith("\n")) {
                        System.err.printf("\n");
                    }
                }
                if (t != null) {
                    System.err.printf("Error: %s\n", t.getMessage());
                }
            }

            @Override
            public void warning(@NonNull String warningFormat, Object... args) {
                if (cli.get().isVerbose()) {
                    System.out.printf("Warning: " + warningFormat, args);
                    if (!warningFormat.endsWith("\n")) {
                        System.out.printf("\n");
                    }
                }
            }

            @Override
            public void info(@NonNull String msgFormat, Object... args) {
                System.out.printf(msgFormat, args);
            }

            @Override
            public void verbose(@NonNull String msgFormat, Object... args) {
                System.out.printf(msgFormat, args);
            }

            /**
             * Used by UpdaterData.acceptLicense() to prompt for license acceptance
             * when updating the SDK from the command-line.
             * <p/>
             * {@inheritDoc}
             */
            @Override
            public int readLine(byte[] inputBuffer) throws IOException {
                return System.in.read(inputBuffer);
            }
        };
    }

    /**
     * Init the application by making sure the SDK path is available and doing basic parsing of the
     * SDK.
     */
    private void init() {
        // We get passed a property for the tools dir
        String toolsDirProp = System.getProperty(TOOLSDIR);
        if (toolsDirProp == null) {
            // for debugging, it's easier to override using the process environment
            toolsDirProp = System.getenv(TOOLSDIR);
        }

        if (toolsDirProp != null) {
            // got back a level for the SDK folder
            File tools;
            if (toolsDirProp.length() > 0) {
                tools = new File(toolsDirProp);
                mOsSdkFolder = tools.getParent();
            } else {
                try {
                    tools = new File(".").getCanonicalFile();
                    mOsSdkFolder = tools.getParent();
                } catch (IOException e) {
                    // Will print an error below since mSdkFolder is not defined
                }
            }
        }

        if (mOsSdkFolder == null) {
            String cmdName = "avdmanager" + (mSdkHandler.getFileOp().isWindows() ? ".bat" : "");

            errorAndExit("The tools directory property is not set, please make sure you are " +
                    "executing %1$s", cmdName);
        }

        // We might get passed a property for the working directory
        // Either it is a valid directory and mWorkDir is set to it's absolute canonical value
        // or mWorkDir remains null.
        String workDirProp = System.getProperty(WORKDIR);
        if (workDirProp == null) {
            workDirProp = System.getenv(WORKDIR);
        }
        if (workDirProp != null) {
            // This should be a valid directory
            /* The working directory, either null or set to an existing absolute canonical directory. */
            File workDir = new File(workDirProp);
            try {
                workDir = workDir.getCanonicalFile().getAbsoluteFile();
            } catch (IOException e) {
                workDir = null;
            }
            if (workDir == null || !workDir.isDirectory()) {
                errorAndExit("The working directory does not seem to be valid: '%1$s", workDirProp);
            }
        }
    }

    /**
     * Does the basic SDK parsing required for all actions
     */
    private void parseSdk() {
        mSdkHandler = AndroidSdkHandler.getInstance(new File(mOsSdkFolder));
    }

    /**
     * Lazily creates and returns an instance of the AVD Manager. The same instance is reused
     * later.
     *
     * @return A non-null AVD Manager instance.
     */
    @NonNull
    private AvdManager getAvdManager() throws AndroidLocation.AndroidLocationException {
        if (mAvdManager == null) {
            mAvdManager = AvdManager.getInstance(mSdkHandler, mSdkLog);
        }
        return mAvdManager;
    }

    /**
     * Actually do an action...
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    private void doAction() {

        String verb = getVerb();
        String directObject = getDirectObject();

        if (AvdManagerCli.VERB_LIST.equals(verb)) {
            // list action.
            if (AvdManagerCli.OBJECT_AVD.equals(directObject)) {
                displayAvdList();

            } else if (AvdManagerCli.OBJECT_DEVICE.equals(directObject)) {
                displayDeviceList();

            } else {
                displayAvdList();
                displayDeviceList();
            }

        } else if (AvdManagerCli.VERB_CREATE.equals(verb)) {
            if (AvdManagerCli.OBJECT_AVD.equals(directObject)) {
                createAvd();
            }
        } else if (AvdManagerCli.VERB_DELETE.equals(verb) &&
                AvdManagerCli.OBJECT_AVD.equals(directObject)) {
            deleteAvd();

        } else if (AvdManagerCli.VERB_MOVE.equals(verb) &&
                AvdManagerCli.OBJECT_AVD.equals(directObject)) {
            moveAvd();

        } else {
            printHelpAndExit(null);
        }
    }

    /**
     * Displays the tags & ABIs valid for the given images.
     */
    private void displayTagAbiList(Collection<SystemImage> systemImages, String message) {
        mSdkLog.info(message);
        if (!systemImages.isEmpty()) {
            boolean first = true;
            for (ISystemImage si : systemImages) {
                if (!first) {
                    mSdkLog.info(", ");
                } else {
                    first = false;
                }
                mSdkLog.info("%s/%s", si.getTag().getId(), si.getAbiType());
            }
            mSdkLog.info("\n");
        } else {
            mSdkLog.info("no ABIs.\n");
        }
    }

    /**
     * Displays the list of available AVDs for the given AvdManager.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    private void displayAvdList(AvdManager avdManager) {

        AvdInfo[] avds = avdManager.getValidAvds();

        // Sort the AVD list by name, to make it stable on different operating systems.
        Arrays.sort(avds, (a1, a2) -> a1.getName().compareToIgnoreCase(a2.getName()));

        // Compact output, suitable for scripts.
        if (getFlagCompact()) {
            char eol = getFlagEolNull() ? '\0' : '\n';

            for (AvdInfo info : avds) {
                mSdkLog.info("%1$s%2$c", info.getName(), eol);
            }

            return;
        }

        mSdkLog.info("Available Android Virtual Devices:\n");

        for (int index = 0; index < avds.length; index++) {
            AvdInfo info = avds[index];
            if (index > 0) {
                mSdkLog.info("---------\n");
            }
            mSdkLog.info("    Name: %s\n", info.getName());

            String deviceName = info.getProperties().get(AvdManager.AVD_INI_DEVICE_NAME);
            String deviceMfctr = info.getProperties().get(AvdManager.AVD_INI_DEVICE_MANUFACTURER);
            if (deviceName != null) {
                mSdkLog.info("  Device: %s", deviceName);
                if (deviceMfctr != null) {
                    mSdkLog.info(" (%s)", deviceMfctr);
                }
                mSdkLog.info("\n");
            }

            mSdkLog.info("    Path: %s\n", info.getDataFolderPath());

            SystemImage img = (SystemImage) info.getSystemImage();
            DetailsTypes.ApiDetailsType details =
                    (DetailsTypes.ApiDetailsType) img.getPackage().getTypeDetails();
            String versionWithCodename = SdkVersionInfo
                    .getVersionWithCodename(details.getAndroidVersion());
            if (details instanceof DetailsTypes.PlatformDetailsType) {
                mSdkLog.info("  API: %s",
                        versionWithCodename);
            } else if (details instanceof DetailsTypes.SysImgDetailsType) {
                IdDisplay vendor = ((DetailsTypes.SysImgDetailsType) details).getVendor();
                String vendorStr = "";
                if (vendor != null) {
                    vendorStr = " (" + vendor.getDisplay() + ")";
                }
                mSdkLog.info("  Target: %s%s\n", img.getTag().getDisplay(), vendorStr);
                mSdkLog.info("          Based on: %s", versionWithCodename);
            } else if (details instanceof DetailsTypes.AddonDetailsType) {
                mSdkLog.info("  Target: %s (%s)\n", img.getPackage().getDisplayName(),
                        ((DetailsTypes.AddonDetailsType) details).getVendor().getDisplay());
                mSdkLog.info("          Based on: %s\n", versionWithCodename);
            }
            mSdkLog.info(" Tag/ABI: %s/%s\n", info.getTag().getId(), info.getAbiType());

            // display some extra values.
            Map<String, String> properties = info.getProperties();
            String skin = properties.get(AvdManager.AVD_INI_SKIN_NAME);
            if (skin != null) {
                mSdkLog.info("    Skin: %s\n", skin);
            }
            String sdcard = properties.get(AvdManager.AVD_INI_SDCARD_SIZE);
            if (sdcard == null) {
                sdcard = properties.get(AvdManager.AVD_INI_SDCARD_PATH);
            }
            if (sdcard != null) {
                mSdkLog.info("  Sdcard: %s\n", sdcard);
            }
            String snapshot = properties.get(AvdManager.AVD_INI_SNAPSHOT_PRESENT);
            if (snapshot != null) {
                mSdkLog.info("Snapshot: %s\n", snapshot);
            }
        }

        // Are there some unused AVDs?
        List<AvdInfo> badAvds = Arrays.asList(avdManager.getAllAvds()).stream().filter(
                avd -> avd.getStatus() != AvdInfo.AvdStatus.OK).collect(Collectors.toList());

        if (badAvds.isEmpty()) {
            return;
        }

        mSdkLog.info("\nThe following Android Virtual Devices could not be loaded:\n");
        boolean needSeparator = false;
        for (AvdInfo info : badAvds) {
            if (needSeparator) {
                mSdkLog.info("---------\n");
            }
            mSdkLog.info("    Name: %s\n", info.getName());
            mSdkLog.info("    Path: %s\n", info.getDataFolderPath());

            String error = info.getErrorMessage();
            mSdkLog.info("   Error: %s\n", error == null ? "Uknown error" : error);
            needSeparator = true;
        }
    }

    /**
     * Displays the list of available AVDs.
     */
    private void displayAvdList() {
        try {
            AvdManager avdManager = getAvdManager();
            displayAvdList(avdManager);
        } catch (AndroidLocation.AndroidLocationException e) {
            errorAndExit(e.getMessage());
        }
    }

    /**
     * Displays the list of available devices.
     */
    private void displayDeviceList() {
        List<Device> devices =
                new ArrayList<>(createDeviceManager().getDevices(DeviceManager.ALL_DEVICES));
        Collections.sort(devices, Device.getDisplayComparator());

        // Compact output, suitable for scripts.
        if (getFlagCompact()) {
            char eol = getFlagEolNull() ? '\0' : '\n';

            for (Device device : devices) {
                mSdkLog.info("%1$s%2$c", device.getId(), eol);
            }

            return;
        }

        // Longer more human-readable output

        mSdkLog.info("Available devices definitions:\n");

        for (int index = 0; index < devices.size(); index++) {
            Device device = devices.get(index);
            if (index > 0) {
                mSdkLog.info("---------\n");
            }
            mSdkLog.info("id: %1$d or \"%2$s\"\n", index, device.getId());
            mSdkLog.info("    Name: %s\n", device.getDisplayName());
            mSdkLog.info("    OEM : %s\n", device.getManufacturer());
            String tag = device.getTagId();
            if (tag != null) {
                mSdkLog.info("    Tag : %s\n", tag);
            }
        }
    }

    @NonNull
    private DeviceManager createDeviceManager() {
        File androidFolder;
        try {
            androidFolder = new File(AndroidLocation.getFolder());
        } catch (AndroidLocation.AndroidLocationException e) {
            mSdkLog.warning(e.getMessage());
            androidFolder = null;
        }
        return DeviceManager.createInstance(
                new File(mOsSdkFolder),
                androidFolder,
                mSdkLog,
                mSdkHandler.getFileOp());
    }

    /**
     * Creates a new AVD. This is a text based creation with command line prompt.
     */
    private void createAvd() {
        ProgressIndicator progress = new ConsoleProgressIndicator();
        String packagePath = getParamPkgPath();
        LocalPackage imagePkg = mSdkHandler.getLocalPackage(packagePath, progress);

        if (imagePkg == null) {
            errorAndExit("Package path is not valid.");
        }
        assert imagePkg != null;

        Collection<SystemImage> sysImgs = mSdkHandler.getSystemImageManager(progress)
                .getImageMap().get(imagePkg);

        if (sysImgs.isEmpty()) {
            errorAndExit("Package %$1s (%$2s) contains no system images.",
                    imagePkg.getDisplayName(), imagePkg.getPath());
        }

        try {
            boolean removePrevious = getFlagForce();
            AvdManager avdManager = getAvdManager();

            String avdName = getParamName();

            if (!RE_AVD_NAME.matcher(avdName).matches()) {
                errorAndExit(
                        "AVD name '%1$s' contains invalid characters.\nAllowed characters are: %2$s",
                        avdName, CHARS_AVD_NAME);
                return;
            }

            AvdInfo info = avdManager.getAvd(avdName, false /*validAvdOnly*/);
            if (info != null) {
                if (removePrevious) {
                    mSdkLog.warning(
                            "Android Virtual Device '%s' already exists and will be replaced.",
                            avdName);
                } else {
                    errorAndExit("Android Virtual Device '%s' already exists.\n" +
                                    "Use --force if you want to replace it.",
                            avdName);
                    return;
                }
            }

            String paramFolderPath = getParamLocationPath();
            File avdFolder;
            if (paramFolderPath != null) {
                avdFolder = new File(paramFolderPath);
            } else {
                avdFolder = AvdInfo
                        .getDefaultAvdFolder(avdManager, avdName, FileOpUtils.create(), false);
            }

            IdDisplay tag = SystemImage.DEFAULT_TAG;
            String abiType = getParamAbi();
            String cmdTag = getParamTag();

            if (abiType != null && abiType.indexOf('/') != -1) {
                String[] segments = abiType.split("/");
                if (segments.length == 2) {
                    if (cmdTag == null) {
                        cmdTag = segments[0];
                    } else if (!cmdTag.equals(segments[0])) {
                        errorAndExit("--%1$s %2$s conflicts with --%3$s %4$s.",
                                AvdManagerCli.KEY_TAG,
                                cmdTag,
                                AvdManagerCli.KEY_ABI,
                                abiType);
                    }
                    abiType = segments[1];
                } else {
                    errorAndExit("Invalid --%1$s %2$s: expected format 'abi' or 'tag/abi'.",
                            AvdManagerCli.KEY_ABI,
                            abiType);
                }
            }

            // if no tag was specified, "default" is implied
            if (cmdTag == null || cmdTag.length() == 0) {
                cmdTag = tag.getId();
            }

            // Collect all possible tags for the selected target
            Set<String> tags = new HashSet<>();
            for (ISystemImage systemImage : sysImgs) {
                tags.add(systemImage.getTag().getId());
            }

            if (!tags.contains(cmdTag)) {
                errorAndExit("Invalid --%1$s %2$s for the selected package.",
                        AvdManagerCli.KEY_TAG,
                        cmdTag);
            }

            SystemImage img = null;

            if (abiType == null || abiType.length() == 0) {
                if (sysImgs.size() == 1) {
                    // Auto-select the single ABI available
                    abiType = sysImgs.iterator().next().getAbiType();
                    img = sysImgs.iterator().next();
                    mSdkLog.info("Auto-selecting single ABI %1$s\n", abiType);
                } else {
                    displayTagAbiList(sysImgs, "Valid ABIs: ");
                    errorAndExit(
                            "This package has more than one ABI. Please specify one using --%1$s.",
                            AvdManagerCli.KEY_ABI);
                }
            } else {
                for (SystemImage systemImage : sysImgs) {
                    if (systemImage.getAbiType().equals(abiType)) {
                        img = systemImage;
                        break;
                    }
                }
                if (img == null) {
                    displayTagAbiList(sysImgs, "Valid ABIs: ");
                    errorAndExit("Invalid --%1$s %2$s for the selected package.",
                            AvdManagerCli.KEY_ABI,
                            abiType);
                }
            }
            assert img != null;

            Device device = null;
            String deviceParam = getParamDevice();
            if (deviceParam != null) {
                List<Device> devices = new ArrayList<>(
                        createDeviceManager().getDevices(DeviceManager.ALL_DEVICES));
                Collections.sort(devices, Device.getDisplayComparator());

                int index = -1;
                try {
                    index = Integer.parseInt(deviceParam);
                } catch (NumberFormatException ignore) {
                }

                if (index >= 0 && index < devices.size()) {
                    device = devices.get(index);
                } else {
                    for (Device d : devices) {
                        if (deviceParam.equals(d.getId())) {
                            device = d;
                            break;
                        }
                    }
                }

                if (device == null) {
                    errorAndExit("No device found matching --%1$s %2$s.",
                            AvdManagerCli.KEY_DEVICE,
                            deviceParam);
                }
            }

            Map<String, String> hardwareConfig = new TreeMap<>();
            if (device != null) {
                // Don't ask user for customized hardware config if a device was selected
                hardwareConfig = DeviceManager.getHardwareProperties(device);
            } else {
                try {
                    Map<String, String> prompted = promptForHardware();
                    if (prompted != null) {
                        hardwareConfig.putAll(prompted);
                    }
                } catch (IOException e) {
                    errorAndExit(e.getMessage());
                }
            }

            if (getParamSdCard() != null) {
                hardwareConfig.put(HardwareProperties.HW_SDCARD, HardwareProperties.BOOLEAN_YES);
            }

            @SuppressWarnings("unused") // newAvdInfo is never read, yet useful for debugging
                    AvdInfo newAvdInfo = avdManager.createAvd(avdFolder,
                    avdName,
                    img,
                    null,
                    null,
                    getParamSdCard(),
                    hardwareConfig,
                    device == null ? null : device.getBootProps(),
                    getFlagSnapshot(),
                    removePrevious,
                    false,
                    mSdkLog);

            if (newAvdInfo == null) {
                errorAndExit("AVD not created.");
            }

        } catch (AndroidLocation.AndroidLocationException e) {
            errorAndExit(e.getMessage());
        }
    }

    /**
     * Delete an AVD. If the AVD name is not part of the available ones look for an invalid AVD (one
     * not loaded due to some error) to remove it too.
     */
    private void deleteAvd() {
        try {
            String avdName = getParamName();
            AvdManager avdManager = getAvdManager();
            AvdInfo info = avdManager.getAvd(avdName, false);

            if (info == null) {
                errorAndExit("There is no Android Virtual Device named '%s'.", avdName);
                return;
            }

            avdManager.deleteAvd(info, mSdkLog);
        } catch (AndroidLocation.AndroidLocationException e) {
            errorAndExit(e.getMessage());
        }
    }

    /**
     * Moves an AVD.
     */
    private void moveAvd() {
        try {
            String avdName = getParamName();
            AvdManager avdManager = getAvdManager();
            AvdInfo info = avdManager.getAvd(avdName, true);

            if (info == null) {
                errorAndExit("There is no valid Android Virtual Device named '%s'.", avdName);
                return;
            }

            // This is a rename if there's a new name for the AVD
            String newName = getParamMoveNewName();
            if (newName != null && newName.equals(info.getName())) {
                // same name, not actually a rename operation
                newName = null;
            }

            // This is a move (of the data files) if there's a new location path
            String paramFolderPath = getParamLocationPath();
            if (paramFolderPath != null) {
                // check if paths are the same. Use File methods to account for OS idiosyncrasies.
                try {
                    File f1 = new File(paramFolderPath).getCanonicalFile();
                    File f2 = new File(info.getDataFolderPath()).getCanonicalFile();
                    if (f1.equals(f2)) {
                        // same canonical path, so not actually a move
                        paramFolderPath = null;
                    }
                } catch (IOException e) {
                    // Fail to resolve canonical path. Fail now since a move operation might fail
                    // later and be harder to recover from.
                    errorAndExit(e.getMessage());
                    return;
                }
            }

            if (newName == null && paramFolderPath == null) {
                mSdkLog.warning("Move operation aborted: same AVD name, same canonical data path");
                return;
            }

            // If a rename was requested and no data move was requested, check if the original
            // data path is our default constructed from the AVD name. In this case we still want
            // to rename that folder too.
            if (newName != null && paramFolderPath == null) {
                // Compute the original data path
                File originalFolder = new File(
                        AndroidLocation.getFolder() + AndroidLocation.FOLDER_AVD,
                        info.getName() + AvdManager.AVD_FOLDER_EXTENSION);
                if (originalFolder.equals(new File(info.getDataFolderPath()))) {
                    try {
                        // The AVD is using the default data folder path based on the AVD name.
                        // That folder needs to be adjusted to use the new name.
                        File f = new File(AndroidLocation.getFolder() + AndroidLocation.FOLDER_AVD,
                                newName + AvdManager.AVD_FOLDER_EXTENSION);
                        paramFolderPath = f.getCanonicalPath();
                    } catch (IOException e) {
                        // Fail to resolve canonical path. Fail now rather than later.
                        errorAndExit(e.getMessage());
                    }
                }
            }

            // Check for conflicts
            if (newName != null) {
                if (avdManager.getAvd(newName, false /*validAvdOnly*/) != null) {
                    errorAndExit("There is already an AVD named '%s'.", newName);
                    return;
                }

                File ini = info.getIniFile();
                if (ini.equals(AvdInfo.getDefaultIniFile(avdManager, newName))) {
                    errorAndExit("The AVD file '%s' is in the way.", ini.getCanonicalPath());
                    return;
                }
            }

            if (paramFolderPath != null && new File(paramFolderPath).exists()) {
                errorAndExit(
                        "There is already a file or directory at '%s'.\nUse --path to specify a different data folder.",
                        paramFolderPath);
            }

            avdManager.moveAvd(info, newName, paramFolderPath, mSdkLog);
        } catch (AndroidLocation.AndroidLocationException | IOException e) {
            errorAndExit(e.getMessage());
        }
    }

    /**
     * Prompts the user to setup a hardware config for a Platform-based AVD.
     */
    @Nullable
    private Map<String, String> promptForHardware() throws IOException {
        byte[] readLineBuffer = new byte[256];
        String result;
        String defaultAnswer = "no";

        mSdkLog.info("Do you wish to create a custom hardware profile [%s]",
                defaultAnswer);

        result = readLine(readLineBuffer).trim();
        // handle default:
        if (result.length() == 0) {
            result = defaultAnswer;
        }

        if (!getBooleanReply(result)) {
            // no custom config, return the skin hardware config in case there is one.
            return null;
        }

        mSdkLog.info("\n"); // empty line

        // get the list of possible hardware properties
        File hardwareDefs = new File(mOsSdkFolder + File.separator +
                SdkConstants.OS_SDK_TOOLS_LIB_FOLDER, SdkConstants.FN_HARDWARE_INI);
        Map<String, HardwareProperties.HardwareProperty> hwMap = HardwareProperties
                .parseHardwareDefinitions(
                        hardwareDefs, null /*sdkLog*/);

        HashMap<String, String> map = new HashMap<>();

        // we just want to loop on the HardwareProperties
        HardwareProperties.HardwareProperty[] hwProperties = hwMap.values().toArray(
                new HardwareProperties.HardwareProperty[hwMap.size()]);
        for (int i = 0; i < hwProperties.length; ) {
            HardwareProperties.HardwareProperty property = hwProperties[i];

            String description = property.getDescription();
            if (description != null) {
                mSdkLog.info("%s: %s\n", property.getAbstract(), description);
            } else {
                mSdkLog.info("%s\n", property.getAbstract());
            }

            String defaultValue = property.getDefault();
            if (defaultValue != null) {
                mSdkLog.info("%s [%s]:", property.getName(), defaultValue);
            } else {
                mSdkLog.info("%s (%s):", property.getName(), property.getType());
            }

            result = readLine(readLineBuffer);
            if (result.length() == 0) {
                if (defaultValue != null) {
                    mSdkLog.info("\n"); // empty line
                    i++; // go to the next property if we have a valid default value.
                    // if there's no default, we'll redo this property
                }
                continue;
            }

            switch (property.getType()) {
                case BOOLEAN:
                    try {
                        if (getBooleanReply(result)) {
                            map.put(property.getName(), "yes");
                            i++; // valid reply, move to next property
                        } else {
                            map.put(property.getName(), "no");
                            i++; // valid reply, move to next property
                        }
                    } catch (IOException e) {
                        // display error, and do not increment i to redo this property
                        mSdkLog.info("\n%s\n", e.getMessage());
                    }
                    break;
                case INTEGER:
                    try {
                        Integer.parseInt(result);
                        map.put(property.getName(), result);
                        i++; // valid reply, move to next property
                    } catch (NumberFormatException e) {
                        // display error, and do not increment i to redo this property
                        mSdkLog.info("\n%s\n", e.getMessage());
                    }
                    break;
                case DISKSIZE:
                    // TODO check validity
                    map.put(property.getName(), result);
                    i++; // valid reply, move to next property
                    break;
            }

            mSdkLog.info("\n"); // empty line
        }

        return map;
    }

    /**
     * Reads a line from the input stream.
     */
    private String readLine(byte[] buffer) throws IOException {
        int count = System.in.read(buffer);

        // is the input longer than the buffer?
        if (count == buffer.length && buffer[count - 1] != 10) {
            // create a new temp buffer
            byte[] tempBuffer = new byte[256];

            // and read the rest
            String secondHalf = readLine(tempBuffer);

            // return a concat of both
            return new String(buffer, 0, count) + secondHalf;
        }

        // ignore end whitespace
        while (count > 0 && (buffer[count - 1] == '\r' || buffer[count - 1] == '\n')) {
            count--;
        }

        return new String(buffer, 0, count);
    }

    /**
     * Reads a line from the input stream, masking it as much as possible.
     */
    @SuppressWarnings("unused")
    private String promptPassword(String prompt) throws IOException {

        // Setup a thread that tries to overwrite any input by
        // masking the last character with a space. This is quite
        // crude but is a documented workaround to the lack of a
        // proper password getter.
        final AtomicBoolean keepErasing = new AtomicBoolean(true);

        Thread eraser = new Thread(() -> {
            while (keepErasing.get()) {
                System.err.print("\b ");    //$NON-NLS-1$. \b=Backspace
                try {
                    Thread.sleep(10 /*millis*/);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }, "eraser");                           //$NON-NLS-1$

        try {
            System.err.print(prompt);
            eraser.start();
            byte[] buffer = new byte[256];
            return readLine(buffer);
        } finally {
            keepErasing.set(false);
            try {
                eraser.join();
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    /**
     * Returns the boolean value represented by the string.
     *
     * @throws IOException If the value is not a boolean string.
     */
    private boolean getBooleanReply(String reply) throws IOException {

        for (String valid : BOOLEAN_YES_REPLIES) {
            if (valid.equalsIgnoreCase(reply)) {
                return true;
            }
        }

        for (String valid : BOOLEAN_NO_REPLIES) {
            if (valid.equalsIgnoreCase(reply)) {
                return false;
            }
        }

        throw new IOException(String.format("%s is not a valid reply", reply));
    }

    private void errorAndExit(String format, Object... args) {
        mSdkLog.error(null, format, args);
        exit(1);
    }

    private void exit(int code) {
        System.exit(code);
    }

    private AvdManagerCli(ILogger logger) {
        super(logger, ACTIONS);

        mSdkLog = logger;
        // The following defines the parameters of the actions defined in mAction.

        // --- generic actions that can work on any verb ---

        define(Mode.BOOLEAN, false,
                GLOBAL_FLAG_VERB, NO_VERB_OBJECT, ""/*shortName*/, KEY_CLEAR_CACHE,
                "Clear the SDK Manager repository manifest cache.", false);

        // --- list avds ---

        define(Mode.BOOLEAN, false,
                VERB_LIST, OBJECT_AVD, "c", KEY_COMPACT,
                "Compact output (suitable for scripts)", false);

        define(Mode.BOOLEAN, false,
                VERB_LIST, OBJECT_AVD, "0", KEY_EOL_NULL,
                "Terminates lines with \\0 instead of \\n (e.g. for xargs -0). Only used by --" +
                        KEY_COMPACT + ".",
                false);

        // --- list targets ---

        define(Mode.BOOLEAN, false,
                VERB_LIST, OBJECT_TARGET, "c", KEY_COMPACT,
                "Compact output (suitable for scripts)", false);

        define(Mode.BOOLEAN, false,
                VERB_LIST, OBJECT_TARGET, "0", KEY_EOL_NULL,
                "Terminates lines with \\0 instead of \\n (e.g. for xargs -0) Only used by --" +
                        KEY_COMPACT + ".",
                false);

        // --- list devices ---

        define(Mode.BOOLEAN, false,
                VERB_LIST, OBJECT_DEVICE, "c", KEY_COMPACT,
                "Compact output (suitable for scripts)", false);

        define(Mode.BOOLEAN, false,
                VERB_LIST, OBJECT_DEVICE, "0", KEY_EOL_NULL,
                "Terminates lines with \\0 instead of \\n (e.g. for xargs -0) Only used by --" +
                        KEY_COMPACT + ".",
                false);

        // --- create avd ---

        define(Mode.STRING, false,
                VERB_CREATE, OBJECT_AVD, "p", KEY_PATH,
                "Directory where the new AVD will be created.", null);
        define(Mode.STRING, true,
                VERB_CREATE, OBJECT_AVD, "n", KEY_NAME,
                "Name of the new AVD.", null);
        define(Mode.STRING, true,
                VERB_CREATE, OBJECT_AVD, "k", KEY_IMAGE_PACKAGE,
                "Target ID of the new AVD.", null);
        define(Mode.STRING, false,
                VERB_CREATE, OBJECT_AVD, "c", KEY_SDCARD,
                "Path to a shared SD card image, or size of a new sdcard for the new AVD.", null);
        define(Mode.BOOLEAN, false,
                VERB_CREATE, OBJECT_AVD, "f", KEY_FORCE,
                "Forces creation (overwrites an existing AVD)", false);
        define(Mode.BOOLEAN, false,
                VERB_CREATE, OBJECT_AVD, "a", KEY_SNAPSHOT,
                "Place a snapshots file in the AVD, to enable persistence.", false);
        define(Mode.STRING, false,
                VERB_CREATE, OBJECT_AVD, "b", KEY_ABI,
                "The ABI to use for the AVD. The default is to auto-select the ABI if the platform has only one ABI for its system images.",
                null);
        define(Mode.STRING, false,
                VERB_CREATE, OBJECT_AVD, "g", KEY_TAG,
                "The sys-img tag to use for the AVD. The default is to auto-select if the platform has only one tag for its system images.",
                null);
        define(Mode.STRING, false,
                VERB_CREATE, OBJECT_AVD, "d", KEY_DEVICE,
                "The optional device definition to use. Can be a device index or id.",
                null);

        // --- delete avd ---

        define(Mode.STRING, true,
                VERB_DELETE, OBJECT_AVD, "n", KEY_NAME,
                "Name of the AVD to delete.", null);

        // --- move avd ---

        define(Mode.STRING, true,
                VERB_MOVE, OBJECT_AVD, "n", KEY_NAME,
                "Name of the AVD to move or rename.", null);
        define(Mode.STRING, false,
                VERB_MOVE, OBJECT_AVD, "r", KEY_RENAME,
                "New name of the AVD.", null);
        define(Mode.STRING, false,
                VERB_MOVE, OBJECT_AVD, "p", KEY_PATH,
                "Path to the AVD's new directory.", null);

    }

    @Override
    public boolean acceptLackOfVerb() {
        return true;
    }

    // -- some helpers for generic action flags

    /**
     * Helper to retrieve the --path value.
     */
    private String getParamLocationPath() {
        return (String) getValue(null, null, KEY_PATH);
    }

    /**
     * Helper to retrieve the --target id value. The id is a string. It can be one of: - an integer,
     * in which case it's the index of the target (cf "android list targets") - a symbolic name such
     * as android-N for platforn API N - a symbolic add-on name such as written in the avd/*.ini
     * files, e.g. "Google Inc.:Google APIs:3"
     */
    private String getParamPkgPath() {
        return (String) getValue(null, null, KEY_IMAGE_PACKAGE);
    }

    /**
     * Helper to retrieve the --name value.
     */
    private String getParamName() {
        return (String) getValue(null, null, KEY_NAME);
    }

    /**
     * Helper to retrieve the --sdcard value.
     */
    private String getParamSdCard() {
        return (String) getValue(null, null, KEY_SDCARD);
    }

    /**
     * Helper to retrieve the --force flag.
     */
    private boolean getFlagForce() {
        return (Boolean) getValue(null, null, KEY_FORCE);
    }

    /**
     * Helper to retrieve the --snapshot flag.
     */
    private boolean getFlagSnapshot() {
        return (Boolean) getValue(null, null, KEY_SNAPSHOT);
    }

    // -- some helpers for avd action flags

    /**
     * Helper to retrieve the --rename value for a move verb.
     */
    private String getParamMoveNewName() {
        return (String) getValue(VERB_MOVE, null, KEY_RENAME);
    }

    /**
     * Helper to retrieve the --abi value.
     */
    private String getParamAbi() {
        return ((String) getValue(null, null, KEY_ABI));
    }

    /**
     * Helper to retrieve the --tag value.
     */
    private String getParamTag() {
        return ((String) getValue(null, null, KEY_TAG));
    }

    /**
     * Helper to retrieve the --device value.
     */
    private String getParamDevice() {
        return ((String) getValue(null, null, KEY_DEVICE));
    }

    // -- some helpers for list avds and list targets flags

    /**
     * Helper to retrieve the --compact value.
     */
    private boolean getFlagCompact() {
        return (Boolean) getValue(null, null, KEY_COMPACT);
    }

    /**
     * Helper to retrieve the --null value.
     */
    private boolean getFlagEolNull() {
        return (Boolean) getValue(null, null, KEY_EOL_NULL);
    }
}
