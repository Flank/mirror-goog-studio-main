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

import com.android.annotations.NonNull;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.TimeoutException;

public class AdbInstaller extends Installer {
    public static final String INSTALLER_BINARY_NAME = "installer";
    public static final String INSTALLER_PATH =
            Deployer.INSTALLER_DIRECTORY + "/" + INSTALLER_BINARY_NAME;
    public static final String ANDROID_EXECUTABLE_PATH =
            "/tools/base/deploy/installer/android-installer";
    private final AdbClient adb;
    private final String installersFolder;
    private final Collection<DeployMetric> metrics;

    private enum OnFail {
        RETRY,
        DO_NO_RETRY
    };

    private final AdbInstallerChannelManager channelsProvider;
    private final Mode mode;

    public enum Mode {
        DAEMON, // Instruct the installer binary on device to keep input fd open and answer
        // requests as they come.

        ONE_SHOT // Instruct the installer to answer to one request and exit.
    }

    public AdbInstaller(
            String installersFolder,
            AdbClient adb,
            Collection<DeployMetric> metrics,
            ILogger logger) {
        this(installersFolder, adb, metrics, logger, Mode.ONE_SHOT);
    }

    public AdbInstaller(
            String installersFolder,
            AdbClient adb,
            Collection<DeployMetric> metrics,
            ILogger logger,
            Mode mode) {
        super(logger);
        this.adb = adb;
        this.installersFolder = installersFolder;
        this.metrics = metrics;
        this.channelsProvider = new AdbInstallerChannelManager(logger, mode);
        this.mode = mode;
    }

    private void logEvents(List<Deploy.Event> events) {
        for (Deploy.Event event : events) {
            if (event.getType() == Deploy.Event.Type.TRC_END) {
                continue;
            }
            logger.info(
                    event.getTimestampNs() / 1000000
                            + "ms "
                            + event.getType()
                            + " ["
                            + event.getPid()
                            + "]["
                            + event.getTid()
                            + "] : "
                            + event.getText());
        }
    }

    protected Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOutMs) throws IOException {
        Trace.begin("./installer " + request.getCommandName());
        long start = System.nanoTime();
        Deploy.InstallerResponse response = sendInstallerRequest(request, OnFail.RETRY, timeOutMs);
        logEvents(response.getEventsList());
        long end = System.nanoTime();

        long maxNs = Long.MIN_VALUE;
        long minNs = Long.MAX_VALUE;
        for (Deploy.Event event : response.getEventsList()) {
            maxNs = Math.max(maxNs, event.getTimestampNs());
            minNs = Math.min(minNs, event.getTimestampNs());
        }
        long delta = ((maxNs + minNs) - (end + start)) / 2;
        Stack<Deploy.Event> eventStack = new Stack<>();
        for (Deploy.Event event : response.getEventsList()) {
            switch (event.getType()) {
                case TRC_BEG:
                case TRC_METRIC:
                    Trace.begin(
                            event.getPid(),
                            event.getTid(),
                            event.getTimestampNs() - delta,
                            event.getText());
                    eventStack.push(event);
                    break;
                case TRC_END:
                    Trace.end(event.getPid(), event.getTid(), event.getTimestampNs() - delta);

                    // If the trace is somehow broken, we don't want to crash studio.
                    if (eventStack.empty()) {
                        break;
                    }

                    Deploy.Event begin = eventStack.pop();

                    // If the trace should be reported as a metric, convert it to a DeployMetric.
                    if (begin.getType() == Deploy.Event.Type.TRC_METRIC) {
                        long startMs = begin.getTimestampNs() - delta;
                        long endMs = event.getTimestampNs() - delta;
                        metrics.add(new DeployMetric(begin.getText(), startMs, endMs));
                    }
                    break;
                default:
                    break;
            }
        }
        Trace.end();
        return response;
    }

    // Invoke command on device. The command must be known by installer android executable.
    // Send content of data into the executable standard input and return a proto buffer
    // object specific to the command.
    @NonNull
    private Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, OnFail onFail, long timeOutMs) throws IOException {
        Deploy.InstallerResponse response = null;

        AdbInstallerChannel channel = channelsProvider.getChannel(adb, getVersion());

        channel.lock();
        try {
            if (channel.writeRequest(request, timeOutMs)) {
                response = channel.readResponse(timeOutMs);
            }
        } catch (TimeoutException e) {
            // If something timed out, don't call into ddmlib to prepare and push the binary
            // again (ddmlib default timeout if 30mn). Fail now.
            String msg = String.format("Device '%s' timed out", adb.getName());
            throw new IOException(msg);
        } finally {
            channel.unlock();
        }

        // Handle the case where the executable is not present on the device.
        // In this case, the
        // shell invocation will return something that is not parsable by protobuffer. Most
        // likely "/system/bin/sh: /data/local/tmp/.studio/bin/installer: not found".
        if (response == null) {
            if (onFail == OnFail.DO_NO_RETRY) {
                // This is the second time this error happens. Aborting.
                throw new IOException("Invalid installer response");
            }
            channelsProvider.reset(adb);
            prepare();
            return sendInstallerRequest(request, OnFail.DO_NO_RETRY, timeOutMs);
        }

        // Parse response.
        if (response.getStatus() == Deploy.InstallerResponse.Status.ERROR_WRONG_VERSION) {
            if (onFail == OnFail.DO_NO_RETRY) {
                // This is the second time this error happens. Aborting.
                throw new IOException("Unrecoverable installer WRONG_VERSION error. Aborting");
            }
            channelsProvider.reset(adb);
            prepare();
            return sendInstallerRequest(request, OnFail.DO_NO_RETRY, timeOutMs);
        }

        Deploy.InstallerResponse.Status status = response.getStatus();
        if (status != Deploy.InstallerResponse.Status.OK) {
            int statusNumber = status.getNumber();
            String errorMsg = response.getErrorMessage();
            String msg =
                    String.format(
                            Locale.US,
                            "Bad InstallerResponse msg='%s', status=%d",
                            errorMsg,
                            statusNumber);
            throw new IOException(msg);
        }

        if (mode == Mode.ONE_SHOT) {
            channelsProvider.reset(adb);
        }

        return response;
    }

    private void prepare() throws IOException {
        File installerFile = null;
        List<String> abis = adb.getAbis();
        // The jar archive contains the android executables:
        // tools/base/deploy/installer/android/x86/installer
        // tools/base/deploy/installer/android/armeabi-v7a/installer
        // tools/base/deploy/installer/android/arm64-v8a/installer
        // Loop over the supported architectures and push it to the drive.
        // TODO: Factor in that an app may be running in 32-bit on a 64-bit device. In this case
        //       we will have to push two binaries. Or we could cut support of 32-bit apps.
        for (String abi : abis) {
            String installerJarPath = abi + "/" + INSTALLER_BINARY_NAME;
            try (InputStream inputStream = getResource(installerJarPath)) {
                // Do we have the device architecture in the jar?
                if (inputStream == null) {
                    continue;
                }
                logger.info("Pushed installer '" + installerJarPath + "'");
                // We have a match, extract it in a tmp file.
                installerFile = File.createTempFile(".studio_installer", abi);
                Files.copy(
                        inputStream,
                        Paths.get(installerFile.getAbsolutePath()),
                        StandardCopyOption.REPLACE_EXISTING);
                break;
            }
        }
        if (installerFile == null) {
            throw new IOException("Unsupported abis: " + Arrays.toString(abis.toArray()));
        }

        cleanAndPushInstaller(installerFile);
        installerFile.delete();
    }

    private void cleanAndPushInstaller(File installerFile) throws IOException {
        runShell(
                new String[] {
                    "rm", "-fr", Deployer.INSTALLER_DIRECTORY, Deployer.INSTALLER_TMP_DIRECTORY
                },
                Timeouts.SHELL_RMFR);
        runShell(
                new String[] {
                    "mkdir", "-p", Deployer.INSTALLER_DIRECTORY, Deployer.INSTALLER_TMP_DIRECTORY
                },
                Timeouts.SHELL_MKDIR);

        // No need to check result here. If something wrong happens, an IOException is thrown.
        adb.push(installerFile.getAbsolutePath(), INSTALLER_PATH);

        runShell(new String[] {"chmod", "+x", INSTALLER_PATH}, Timeouts.SHELL_CHMOD);
    }

    private void runShell(String[] cmd, long timeOutMs) throws IOException {
        byte[] response = adb.shell(cmd, timeOutMs);

        if (response.length <= 0) {
            return;
        }

        // An error has occurred.
        String extraMsg = new String(response, Charsets.UTF_8).trim();
        String error = String.format("Cannot '%s' : '%s'", String.join(" ", cmd), extraMsg);
        logger.error(null, error);
        throw new IOException(error);
    }

    private InputStream getResource(String path) throws FileNotFoundException {
        InputStream stream;
        if (this.installersFolder == null) {
            stream = Installer.class.getResourceAsStream(ANDROID_EXECUTABLE_PATH + "/" + path);
        } else {
            stream = new FileInputStream(installersFolder + "/" + path);
        }
        return stream;
    }

    // An asymmetry is when the "extra" response contained in an InstallerResponse does not match
    // the "extra" request in the InstallerRequest. e.g.: If an InstallerRequest with a DumpRequest
    // was sent, the response received should contain a DumpResponse.
    //
    // This could happen in daemom mode, if a request is sent but the response is not read. This
    // case would create a "desync" where the previous response (stored in the socket buffer)
    // would be read without change to recovery.
    //
    // To solve this issue, we reset the connection to the daemon.
    protected void onAsymetry(Deploy.InstallerRequest req, Deploy.InstallerResponse resp)
            throws IOException {
        try {
            channelsProvider.reset(adb);
        } catch (IOException e) {
            // ignore
        }

    }
}
