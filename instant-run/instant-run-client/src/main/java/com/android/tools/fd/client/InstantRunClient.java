/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.fd.client;

import static com.android.tools.fd.client.InstantRunArtifactType.DEX;
import static com.android.tools.fd.client.InstantRunArtifactType.SPLIT;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_EOF;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_PATCHES;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_PING;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_RESTART_ACTIVITY;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_SHELL_COMMAND;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_SHOW_TOAST;
import static com.android.tools.fd.common.ProtocolConstants.PROTOCOL_IDENTIFIER;
import static com.android.tools.fd.common.ProtocolConstants.PROTOCOL_VERSION;
import static com.android.tools.fd.runtime.Paths.getDeviceIdFolder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.fd.runtime.ApplicationPatch;
import com.android.tools.fd.runtime.Paths;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.common.io.Files;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class InstantRunClient {

    /** Local port on the desktop machine via which we tunnel to the Android device */
    // Note: just a random number, hopefully it is a free/available port on the host
    private static final int DEFAULT_LOCAL_PORT = 46622;

    /** Prefix for classes.dex files */
    private static final String CLASSES_DEX_PREFIX = "classes";

    /** Suffix for classes.dex files */
    private static final String CLASSES_DEX_SUFFIX = ".dex";

    /**
     * Instead of writing to the data folder, we can read/write to a local temp file instead.
     * This is required because some devices (Samsung Galaxy Edge atleast)
     * doesn't allow access into the package folder even with run-as.
     */
    public static final boolean USE_BUILD_ID_TEMP_FILE =
            !Boolean.getBoolean("instantrun.use_datadir");

    // TODO:  Should probably get this from ManifestMerger2 or SDKConstants.
    private static final String RUN_AS_SERVICE_PKG_NAME =
            "com.android.tools.fd.runtime.RunAsServer";

    @NonNull
    private final String mPackageName;

    @NonNull
    private final ILogger mLogger;

    private final long mToken;

    private final ServiceCommunicator mAppService;
    private ServiceCommunicator mRunAsService;

    public InstantRunClient(
            @NonNull String packageName,
            @NonNull ILogger logger,
            long token) {
        this(packageName, logger, token, DEFAULT_LOCAL_PORT);
    }

    @VisibleForTesting
    public InstantRunClient(
            @NonNull String packageName,
            @NonNull ILogger logger,
            long token,
            int port) {
        mAppService = new ServiceCommunicator(packageName, logger, port);
        mPackageName = packageName;
        mLogger = logger;
        mToken = token;
    }

    @NonNull
    private static String copyToDeviceScratchFile(@NonNull IDevice device, @NonNull String pkgName,
            @NonNull String contents)
            throws IOException, AdbCommandRejectedException, SyncException, TimeoutException {

        File local = null;
        try {
            local = createTempFile("data", "fdr");
            Files.write(contents.getBytes(Charsets.UTF_8), local);
            return copyToDeviceScratchFile(device, pkgName, local);
        } finally {
            if (local != null) {
                //noinspection ResultOfMethodCallIgnored
                local.delete();
            }
        }
    }

    @NonNull
    private static String copyToDeviceScratchFile(@NonNull IDevice device, @NonNull String pkgName,
            @NonNull File local) throws IOException, AdbCommandRejectedException,
            SyncException, TimeoutException {
        String remoteTmpBuildId = Paths.DEVICE_TEMP_DIR + "/" + pkgName + "-data.fdr";
        device.pushFile(local.getAbsolutePath(), remoteTmpBuildId);
        return remoteTmpBuildId;
    }

    private static int getMaxDexFileNumber(@NonNull String fileListing) {
        int max = -1;

        for (String name : Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings()
                .splitToList(fileListing)) {
            if (name.startsWith(CLASSES_DEX_PREFIX) && name.endsWith(CLASSES_DEX_SUFFIX)) {
                String middle = name.substring(CLASSES_DEX_PREFIX.length(),
                        name.length() - CLASSES_DEX_SUFFIX.length());
                try {
                    int version = Integer.decode(middle);
                    if (version > max) {
                        max = version;
                    }
                } catch (NumberFormatException ignore) {
                }
            }
        }

        return max;
    }

    private static File createTempFile(String prefix, String suffix) throws IOException {
        //noinspection SSBasedInspection Tests use this in tools/base
        File file = File.createTempFile(prefix, suffix);
        file.deleteOnExit();
        return file;
    }

    /**
     * Attempts to connect to a given device and sees if an instant run enabled app is running
     * there.
     */
    @NonNull
    public AppState getAppState(@NonNull IDevice device) throws IOException {
        return mAppService.talkToService(device,
                new Communicator<AppState>() {
                    @Override
                    public AppState communicate(@NonNull DataInputStream input,
                            @NonNull DataOutputStream output) throws IOException {
                        output.writeInt(MESSAGE_PING);
                        boolean foreground = input.readBoolean(); // Wait for "pong"
                        mLogger.info(
                            "Ping sent and replied successfully, "
                            + "application seems to be running. Foreground=" + foreground);
                        return foreground ? AppState.FOREGROUND : AppState.BACKGROUND;
                    }
                });
    }


    public void showToast(@NonNull IDevice device, @NonNull final String message)
            throws IOException {
        mAppService.talkToService(device, new Communicator<Boolean>() {
            @Override
            public Boolean communicate(@NonNull DataInputStream input,
                    @NonNull DataOutputStream output) throws IOException {
                output.writeInt(MESSAGE_SHOW_TOAST);
                output.writeUTF(message);
                return false;
            }
        });
    }

    /**
     * Restart the activity on this device, if it's running and is in the foreground.
     */
    public void restartActivity(@NonNull IDevice device) throws IOException {
        AppState appState = getAppState(device);
        if (appState == AppState.FOREGROUND || appState == AppState.BACKGROUND) {
            mAppService.talkToService(device, new Communicator<Void>() {
                @Override
                public Void communicate(@NonNull DataInputStream input,
                        @NonNull DataOutputStream output) throws IOException {
                    output.writeInt(MESSAGE_RESTART_ACTIVITY);
                    writeToken(output);
                    return null;
                }
            });
        }
    }

    public UpdateMode pushPatches(@NonNull IDevice device,
            @NonNull final InstantRunBuildInfo buildInfo,
            @NonNull UpdateMode updateMode,
            final boolean isRestartActivity,
            final boolean isShowToastEnabled) throws InstantRunPushFailedException, IOException {
        if (!buildInfo.canHotswap()) {
            updateMode = updateMode.combine(UpdateMode.COLD_SWAP);
        }

        List<FileTransfer> files = Lists.newArrayList();

        boolean appInForeground;
        boolean appRunning;
        try {
            AppState appState = getAppState(device);
            appInForeground = appState == AppState.FOREGROUND;
            appRunning = appState == AppState.FOREGROUND || appState == AppState.BACKGROUND;
        } catch (IOException e) {
            appInForeground = appRunning = false;
        }

        List<InstantRunArtifact> artifacts = buildInfo.getArtifacts();
        mLogger.info("Artifacts from build-info.xml: " + Joiner.on("-").join(artifacts));
        for (InstantRunArtifact artifact : artifacts) {
            InstantRunArtifactType type = artifact.type;
            File file = artifact.file;
            switch (type) {
                case MAIN:
                    // Should never be used with this method: APKs should be pushed by DeployApkTask
                    assert false : artifact;
                    break;
                case SPLIT_MAIN:
                    // Should only be used here when we're doing a *compatible*
                    // resource swap and also got an APK for split. Ignore here.
                    continue;
                case SPLIT:
                    // Should never be used with this method: APK splits should
                    // be pushed by SplitApkDeployTask
                    assert false : artifact;
                    break;
                case RESOURCES:
                    updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
                    files.add(FileTransfer.createResourceFile(file));
                    break;
                case DEX:
                    String name = file.getParentFile().getName() + "-" + file.getName();
                    files.add(FileTransfer.createSliceDex(file, name));
                    break;
                case RELOAD_DEX:
                    if (appInForeground) {
                        files.add(FileTransfer.createHotswapPatch(file));
                    } else {
                        // Gradle created a reload dex, but the app is no longer running.
                        // If it created a cold swap artifact, we can use it; otherwise we're out of luck.
                        if (!buildInfo.hasOneOf(DEX, SPLIT)) {
                            throw new InstantRunPushFailedException(
                                "Can't apply hot swap patch: app is no longer running");
                        }
                    }
                    break;
                default:
                    assert false : artifact;
            }
        }

        boolean needRestart;

        if (appRunning) {
            List<ApplicationPatch> changes = new ArrayList<ApplicationPatch>(files.size());
            for (FileTransfer file : files) {
                try {
                    changes.add(file.getPatch());
                }
                catch (IOException e) {
                    throw new InstantRunPushFailedException("Could not read file " + file);
                }
            }
            updateMode = pushPatches(device, buildInfo.getTimeStamp(), changes,
                                     updateMode, isRestartActivity, isShowToastEnabled);

            needRestart = false;
            if (!appInForeground || !buildInfo.canHotswap()) {
                stopApp(device, false /* sendChangeBroadcast */);
                needRestart = true;
            }
        }
        else {
            // Push to data directory
            pushFiles(files, device, buildInfo.getTimeStamp());
            needRestart = true;
        }

        logFilesPushed(files, needRestart);

        if (needRestart) {
            // TODO: this should not need to be explicit, but leaving in to ensure no behaviour change.
            return UpdateMode.COLD_SWAP;
        }
        return updateMode;
    }

    public UpdateMode pushPatches(@NonNull IDevice device,
            @NonNull final String buildId,
            @NonNull final List<ApplicationPatch> changes,
            @NonNull UpdateMode updateMode,
            final boolean isRestartActivity,
            final boolean isShowToastEnabled) throws IOException {
        if (changes.isEmpty() || updateMode == UpdateMode.NO_CHANGES) {
            // Sync the build id to the device; Gradle might rev the build id
            // even when there are no changes, and we need to make sure that the
            // device id reflects this new build id, or the next build will
            // discover different id's and will conclude that it needs to do a
            // full rebuild
            transferLocalIdToDeviceId(device, buildId);

            return UpdateMode.NO_CHANGES;
        }

        if (updateMode == UpdateMode.HOT_SWAP && isRestartActivity) {
            updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
        }

        final UpdateMode updateMode1 = updateMode;
        mAppService.talkToService(device, new Communicator<Boolean>() {
            @Override
            public Boolean communicate(@NonNull DataInputStream input,
                    @NonNull DataOutputStream output) throws IOException {
                output.writeInt(MESSAGE_PATCHES);
                writeToken(output);
                ApplicationPatchUtil.write(output, changes, updateMode1);

                // Let the app know whether it should show toasts
                output.writeBoolean(isShowToastEnabled);

                // Finally read a boolean back from the other side; this has the net effect of
                // waiting until applying/verifying code on the other side is done. (It doesn't
                // count the actual restart time, but for activity restarts it's typically instant,
                // and for cold starts we have no easy way to handle it (the process will die and a
                // new process come up; to measure that we'll need to work a lot harder.)
                input.readBoolean();

                return false;
            }

            @Override
            int getTimeout() {
                return 8000; // allow up to 8 seconds for resource push
            }
        });

        transferLocalIdToDeviceId(device, buildId);

        return updateMode;
    }

    /**
     * Called after a build &amp; successful push to device: updates the build id on the device to
     * whatever the build id was assigned by Gradle.
     *
     * @param device the device to push to
     */
    public void transferLocalIdToDeviceId(@NonNull IDevice device, @NonNull String buildId) {
        transferBuildIdToDevice(device, buildId, mPackageName, mLogger);
    }

    // Note: This method can be called even if IR is turned off, as even when IR is off, we want to
    // trash any existing build ids saved on the device.
    public static void transferBuildIdToDevice(@NonNull IDevice device,
            @NonNull String buildId,
            @NonNull String pkgName,
            @Nullable ILogger logger) {
        if (logger == null) {
            logger = new NullLogger();
        }
        final long unused = 0L;
        InstantRunClient client = new InstantRunClient(pkgName, logger, unused);
        client.transferBuildIdToDevice(device, buildId);
    }

    private void transferBuildIdToDevice(@NonNull IDevice device, @NonNull String buildId) {
        try {
            if (USE_BUILD_ID_TEMP_FILE) {
                String remoteIdFile = getDeviceIdFolder(mPackageName);
                //noinspection SSBasedInspection This should work
                File local = File.createTempFile("build-id", "txt");
                local.deleteOnExit();
                Files.write(buildId, local, Charsets.UTF_8);
                device.pushFile(local.getPath(), remoteIdFile);
            } else {
                String remote = copyToDeviceScratchFile(device, mPackageName, buildId);
                String dataDir = Paths.getDataDirectory(mPackageName);
                // Escape any single quotes that may be in the data directory.
                // We control it, so its highly unlikely, but it's slightly safer this way.
                dataDir = shellEscape(dataDir);

                // We used to do this here:
                // String cmd = "run-as " + pkg + " mkdir -p " + dataDir +
                //   "; run-as " + pkg + " cp " + remote + " " + dataDir + "/" + BUILD_ID_TXT;
                // but it turns out "cp" is missing on API 15!
                // Let's use cat and sh instead which seems to be available everywhere.
                // (Note: echo is not, it's missing on API 19.)
                boolean result = runAsCommand(device, "mkdir -p " + dataDir);
                if (result) {
                    // There are currently two possible code paths for runAsCommand:
                    //  1) First it tries "run-as <user> <cmd>"
                    //  2) If that fails, it tries the RunAsServer.
                    // In both cases, the server passes <cmd> as a single argument
                    // to the shell via execvp, with no additional escaping.
                    runAsCommand(device, "cat > " + dataDir + "/" + Paths.BUILD_ID_TXT);
                }
            }
        } catch (IOException ioe) {
            mLogger.warning("Couldn't write build id file: %s", ioe);
        } catch (AdbCommandRejectedException
                 | TimeoutException
                 | SyncException
                 | ShellCommandUnresponsiveException e) {
            mLogger.warning("%s", Throwables.getStackTraceAsString(e));
        }
    }

    private String shellEscape(@NonNull String str) {
        Escapers.Builder builder = Escapers.builder();
        builder.addEscape('\'', "'\"'\"'");
        Escaper e = builder.build();
        return e.escape(str);
    }

    /**
     * Returns the build timestamp on the device, or null if it is not found.
     */
    @Nullable
    public String getDeviceBuildTimestamp(@NonNull IDevice device) {
        return getDeviceBuildTimestamp(device, mPackageName, mLogger);
    }

    @Nullable
    public static String getDeviceBuildTimestamp(@NonNull IDevice device,
            @NonNull String packageName, @NonNull ILogger logger) {
        try {
            if (USE_BUILD_ID_TEMP_FILE) {
                String remoteIdFile = getDeviceIdFolder(packageName);
                File localIdFile = createTempFile("build-id", "txt");
                try {
                    device.pullFile(remoteIdFile, localIdFile.getPath());
                    return Files.toString(localIdFile, Charsets.UTF_8).trim();
                } catch (SyncException ignore) {
                    return null;
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    localIdFile.delete();
                }
            } else {
                String remoteIdFile = Paths.getDataDirectory(packageName) + "/" + Paths.BUILD_ID_TXT;
                CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                device.executeShellCommand("run-as " + packageName + " cat " + remoteIdFile,
                                           receiver);
                String output = receiver.getOutput().trim();
                String id;
                if (output.contains(":")) { // cat: command not found, cat: permission denied etc
                    if (output.startsWith(remoteIdFile)) {
                        // /data/data/my.pkg.path/files/instant-run/build-id.txt: No such file or directory
                        return null;
                    }
                    // On a user device, we cannot pull from a path where the segments
                    // aren't readable (I think this is a ddmlib limitation).
                    // So we first copy to /data/local/tmp and pull from there..
                    String remoteTmpFile = "/data/local/tmp/build-id.txt";
                    device.executeShellCommand("cp " + remoteIdFile + " " + remoteTmpFile, receiver);
                    output = receiver.getOutput().trim();
                    if (!output.isEmpty()) {
                        logger.info(output);
                    }
                    File localIdFile = createTempFile("build-id", "txt");
                    device.pullFile(remoteTmpFile, localIdFile.getPath());
                    id = Files.toString(localIdFile, Charsets.UTF_8).trim();
                    //noinspection ResultOfMethodCallIgnored
                    localIdFile.delete();
                } else {
                    id = output;
                }
                return id;
            }
        } catch (IOException ignore) {
        } catch (AdbCommandRejectedException
                 | SyncException
                 | TimeoutException
                 | ShellCommandUnresponsiveException e) {
            logger.warning("%s", Throwables.getStackTraceAsString(e));
        }

        return null;
    }

    private void writeToken(@NonNull DataOutputStream output) throws IOException {
        output.writeLong(mToken);
    }

    /**
     * Transfer the file as a slice/sharded dex file. This means
     * that its remote path should be the slice name, in the dex
     * directory.
     */
    public static final int TRANSFER_MODE_SLICE = 1;

    /**
     * Transfer the file as a hotswap overlay file. This means
     * that its remote path should be a temporary file.
     */
    public static final int TRANSFER_MODE_HOTSWAP = 3;

    /**
     * Transfer the file as a resource file. This means that it
     * should be written to the inactive resource file section
     * in the app data directory.
     */
    public static final int TRANSFER_MODE_RESOURCES = 4;

    /**
     * File to be transferred to the device. For use with
     * {@link #pushFiles(List, IDevice, String)}
     */
    public static class FileTransfer {
        public final int mode;
        public final File source;
        public final String name;

        public FileTransfer(int mode, @NonNull File source, @NonNull String name) {
            this.mode = mode;
            this.source = source;
            this.name = name;
        }

        @NonNull
        public static FileTransfer createSliceDex(@NonNull File source, @NonNull String name) {
            return new FileTransfer(TRANSFER_MODE_SLICE, source, name);
        }

        @NonNull
        public static FileTransfer createResourceFile(@NonNull File source) {
            return new FileTransfer(TRANSFER_MODE_RESOURCES, source, Paths.RESOURCE_FILE_NAME);
        }

        @NonNull
        public static FileTransfer createHotswapPatch(@NonNull File source) {
            return new FileTransfer(TRANSFER_MODE_HOTSWAP, source, Paths.RELOAD_DEX_FILE_NAME);
        }

        @NonNull
        public ApplicationPatch getPatch() throws IOException {
            byte[] bytes = Files.toByteArray(source);
            String path;
            // These path names are specially handled on the client side
            // (e.g. it interprets "classes.dex" as meaning create a new
            // unique class file in the class folder.
            switch (mode) {
                case TRANSFER_MODE_SLICE:
                    path = Paths.DEX_SLICE_PREFIX + name;
                    break;
                case TRANSFER_MODE_HOTSWAP:
                case TRANSFER_MODE_RESOURCES:
                    path = name;
                    break;
                default:
                    throw new IllegalArgumentException(Integer.toString(mode));
            }

            return new ApplicationPatch(path, bytes);
        }

        @Override
        public String toString() {
            return source + " as " + name + " with mode " + mode;
        }
    }

    /**
     * Stops the given app (via adb).
     *
     * @param device the device
     * @param sendChangeBroadcast whether to also send a package change broadcast
     * @throws InstantRunPushFailedException if there's a problem
     */
    public void stopApp(@NonNull IDevice device, boolean sendChangeBroadcast)
            throws InstantRunPushFailedException {
        try {
            runCommand(device, "am force-stop " + mPackageName);
        } catch (Throwable t) {
            throw new InstantRunPushFailedException("Exception while stopping app: " + t);
        }
        if (sendChangeBroadcast) {
            try {
                // We think this might necessary to force the system not hold on
                // to any data from the previous version of the process, such as
                // the scenario described in
                // https://code.google.com/p/android/issues/detail?id=200895#c9
                runCommand(device, "am broadcast -a android.intent.action.PACKAGE_CHANGED -p "
                           + mPackageName);
            } catch (Throwable ignore) {
                // We can live with this one not succeeding; may require root etc
                // See https://code.google.com/p/android/issues/detail?id=201249
            }
        }
    }

    /**
     * Install dex and resource files on the given device when the app isn't running,
     * using adb since we can't send it patches via the socket connection.
     *
     * @param files the files to push to the device, and the paths to push them as.
     * @param device   the device to push to
     */
    public void pushFiles(
            @NonNull List<FileTransfer> files,
            @NonNull IDevice device,
            @NonNull final String buildId) throws InstantRunPushFailedException {
        try {
            Set<String> createdDirs = Sets.newHashSet();

            for (FileTransfer file : files) {
                String folder;
                String name;
                switch (file.mode) {
                    case TRANSFER_MODE_SLICE:
                        folder = Paths.getDexFileDirectory(mPackageName);
                        name = Paths.DEX_SLICE_PREFIX + file.name;
                        break;
                    case TRANSFER_MODE_RESOURCES:
                        folder = Paths.getInboxDirectory(mPackageName);
                        name = Paths.RESOURCE_FILE_NAME;
                        break;
                    case TRANSFER_MODE_HOTSWAP:
                        throw new IllegalArgumentException("Hotswap patches can only be applied "
                                + "when the app is running");
                    default:
                        throw new IllegalArgumentException(Integer.toString(file.mode));
                }

                // Copy the restart .dex file over to the device in the dex folder with the new name
                String remote = copyToDeviceScratchFile(device, mPackageName, file.source);

                // Make sure directory exists
                if (!createdDirs.contains(folder)) {
                    createdDirs.add(folder);
                    String cmd = "mkdir -p " + folder;
                    if (!runAsCommand(device, cmd)) {
                        mLogger.warning("pushFiles: %s", "Error creating folder with: " + cmd);
                        throw new InstantRunPushFailedException("Error creating folder with: " + cmd);
                    }
                }

                String cmd = "cp " + remote + " " + folder + "/" + name;
                if (!runAsCommand(device, cmd)) {
                    mLogger.warning("pushFiles: %s", "Error copying file with: " + cmd);
                    throw new InstantRunPushFailedException("Error copying file with: " + cmd);
                }
            }

            transferLocalIdToDeviceId(device, buildId);
        } catch (IOException ioe) {
            mLogger.warning("Couldn't write build id file: %s", ioe);
            throw new InstantRunPushFailedException("IOException while pushing files: " + ioe);
        } catch (AdbCommandRejectedException
                 | TimeoutException
                 | ShellCommandUnresponsiveException
                 | SyncException e) {
            mLogger.warning("%s", Throwables.getStackTraceAsString(e));
            throw new InstantRunPushFailedException("Exception while pushing files: " + e);
        }
    }

    private boolean runAsCommand(@NonNull IDevice device, @NonNull final String cmd)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        // Try using adb with run-as.  Eventually this code path will go away.
        // You can set FORCE_RUN_AS_SERVER to true to force the RunAsServer code path,
        // for debugging.
        boolean FORCE_RUN_AS_SERVER = false;
        if (!FORCE_RUN_AS_SERVER) {
            String runAsCmd = "run-as " + mPackageName + " " + cmd;
            String output = getCommandOutput(device, runAsCmd).trim();
            if (output.isEmpty()) {
                return true;  // Worked.
            }
            // These strings are from com.android.tools.idea.fd.RunAsValidityService:
            if (!(output.contains("run-as: Package '")
                  || output.contains("run-as: Could not set capabilities"))) {
                // Some other error occurred.
                mLogger.warning("Unexpected shell output for " + runAsCmd + ": " + output);
                return false;
            }
        }

        // The device has a broken run-as:  Fall back to talking to the service.
        // At some point, when we're sure the service is well-tested, we can eliminate
        // the adb/run-as path above, and always use the service.
        ensureRunAsService(device);

        return mRunAsService.talkToService(device,
                new Communicator<Boolean>() {
                    @Override
                    public Boolean communicate(@NonNull DataInputStream input,
                            @NonNull DataOutputStream output) throws IOException {
                        output.writeInt(MESSAGE_SHELL_COMMAND);
                        writeToken(output);
                        output.writeUTF(cmd);  // Don't include "run-as <pkg>" prefix.
                        boolean result = input.readBoolean();
                        if (result) {
                            mLogger.info("Run-as service successfully executed '" + cmd + "'");
                        } else {
                            mLogger.warning("Run-as service failed to execute '" + cmd + "'");
                        }
                        return result;
                    }
                });
    }

    private boolean runCommand(@NonNull IDevice device, @NonNull String cmd)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        String output = getCommandOutput(device, cmd).trim();
        if (!output.isEmpty()) {
            mLogger.warning("Unexpected shell output for " + cmd + ": " + output);
            return false;
        }
        return true;
    }

    @NonNull
    private static String getCommandOutput(@NonNull IDevice device, @NonNull String cmd)
            throws TimeoutException, AdbCommandRejectedException,
            ShellCommandUnresponsiveException, IOException {
        CollectingOutputReceiver receiver;
        receiver = new CollectingOutputReceiver();
        device.executeShellCommand(cmd, receiver);
        return receiver.getOutput();
    }

    private void logFilesPushed(@NonNull List<FileTransfer> files, boolean needRestart) {
        StringBuilder sb = new StringBuilder("Pushing files: ");
        if (needRestart) {
            sb.append("(needs restart) ");
        }

        sb.append('[');
        String separator = "";
        for (FileTransfer file : files) {
            sb.append(separator);
            sb.append(file.source.getName());
            sb.append(" as ");
            sb.append(file.name);

            separator = ", ";
        }
        sb.append(']');

        mLogger.info(sb.toString());
    }

    private void ensureRunAsService(@NonNull IDevice device) throws IOException {
        if (!isRunAsServiceRunning(device)) {
            startRunAsService(device);
            waitForRunAsService(device);
        }
    }

    /**
     * Begins the startup of the run-as service, via an adb command.
     */
    private void startRunAsService(@NonNull IDevice device) throws IOException {
        try {
            String serviceName = mPackageName + "/" + RUN_AS_SERVICE_PKG_NAME;
            String cmd = "am startservice " + serviceName;
            String output = getCommandOutput(device, cmd).trim();
            if (!output.startsWith("Starting service:")) {
                throw new IOException("am produced unexpected output");
            }
        } catch (Throwable t) {
            throw new IOException("Error starting run-as service: " + t);
        }
        // Just use port after the one we use for the in-process service.
        int port = mAppService.getLocalPort() + 1;
        mRunAsService = new ServiceCommunicator(mPackageName + ".runas", mLogger, port);
    }

    private boolean isRunAsServiceRunning(@NonNull IDevice device) {
        if (mRunAsService == null) {
            return false;
        }
        try {
            return mRunAsService.talkToService(device,
                    new Communicator<Boolean>() {
                        @Override
                        public Boolean communicate(@NonNull DataInputStream input,
                                @NonNull DataOutputStream output) throws IOException {
                            output.writeInt(MESSAGE_PING);
                            boolean result = input.readBoolean(); // Wait for "pong"
                            mLogger.info("RunAs Service ping sent and replied successfully");
                            return result;
                        }
                    });
        } catch (IOException iox) {
            // Service may have died, or may not have been started yet.
            return false;
        }
    }

    /**
     * Polls until the run-as service starts up.
     */
    private void waitForRunAsService(@NonNull IDevice device) throws IOException {
        final int WAIT_SECONDS = 5;
        long maxWaitMillis = TimeUnit.SECONDS.toMillis(WAIT_SECONDS);
        long waitedMillis = 0L;
        long interval = 1L;
        try {
            mLogger.info("Waiting up to " + WAIT_SECONDS + " sec for run-as service...");
            while (waitedMillis <= maxWaitMillis) {
                Thread.currentThread().sleep(interval);
                if (isRunAsServiceRunning(device)) {
                    return;  // Successful ping.
                }
                waitedMillis += interval;
                interval *= 2;  // Exponential backoff.
            }
        } catch (InterruptedException ix) {
        }
        throw new IOException("Timed out waiting for run-as server to start up.");
    }
}
