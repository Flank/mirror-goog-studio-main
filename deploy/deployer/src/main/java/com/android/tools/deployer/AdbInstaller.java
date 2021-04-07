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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.idea.protobuf.CodedInputStream;
import com.android.tools.idea.protobuf.CodedOutputStream;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

public class AdbInstaller implements Installer {
    public static final String INSTALLER_BINARY_NAME = "installer";
    public static final String INSTALLER_PATH =
            Deployer.INSTALLER_DIRECTORY + "/" + INSTALLER_BINARY_NAME;
    public static final String ANDROID_EXECUTABLE_PATH =
            "/tools/base/deploy/installer/android-installer";
    private final AdbClient adb;
    private final String installersFolder;
    private final Collection<DeployMetric> metrics;
    private final ILogger logger;

    // MessagePipeWrapper magic number; should be kept in sync with message_pipe_wrapper.cc
    private static final byte[] MAGIC_NUMBER = {
        (byte) 0xAC,
        (byte) 0xA5,
        (byte) 0xAC,
        (byte) 0xA5,
        (byte) 0xAC,
        (byte) 0xA5,
        (byte) 0xAC,
        (byte) 0xA5
    };

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
        this.adb = adb;
        this.installersFolder = installersFolder;
        this.metrics = metrics;
        this.logger = logger;
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

    @Override
    public Deploy.InstallCoroutineAgentResponse installCoroutineAgent(
            String packageName, Deploy.Arch arch) throws IOException {
        Deploy.InstallCoroutineAgentRequest.Builder installCoroutineAgentRequestBuilder =
                Deploy.InstallCoroutineAgentRequest.newBuilder();
        installCoroutineAgentRequestBuilder.setPackageName(packageName).setArch(arch);
        Deploy.InstallerRequest.Builder request =
                buildRequest("installcoroutineagent")
                        .setInstallCoroutineAgentRequest(installCoroutineAgentRequestBuilder);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.InstallCoroutineAgentResponse response =
                installerResponse.getInstallCoroutineAgentResponse();
        logger.verbose("installer install coroutine agent: " + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.DumpResponse dump(List<String> packageNames) throws IOException {
        Deploy.DumpRequest.Builder dumpRequestBuilder = Deploy.DumpRequest.newBuilder();
        for (String packageName : packageNames) {
            dumpRequestBuilder.addPackageNames(packageName);
        }
        Deploy.InstallerRequest.Builder request =
                buildRequest("dump").setDumpRequest(dumpRequestBuilder);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.DumpResponse response = installerResponse.getDumpResponse();
        logger.verbose("installer dump: " + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.SwapResponse swap(Deploy.SwapRequest swapRequest) throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("swap");
        request.setSwapRequest(swapRequest);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.SwapResponse response = installerResponse.getSwapResponse();
        logger.verbose("installer swap: " + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.SwapResponse overlaySwap(Deploy.OverlaySwapRequest overlaySwapRequest)
            throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("overlayswap");
        request.setOverlaySwapRequest(overlaySwapRequest);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.SwapResponse response = installerResponse.getSwapResponse();
        logger.verbose("installer overlayswap: " + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.OverlayInstallResponse overlayInstall(
            Deploy.OverlayInstallRequest overlayInstallRequest) throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("overlayinstall");
        request.setOverlayInstall(overlayInstallRequest);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.OverlayInstallResponse response = installerResponse.getOverlayInstallResponse();
        logger.verbose("installer overlayinstall: " + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.OverlayIdPushResponse verifyOverlayId(String packageName, String oid)
            throws IOException {
        // Doing a overylayid push with both new and old OID as the argument effectively verifies
        // the OID without updating it.
        Deploy.OverlayIdPush overlayIdPushRequest =
                createOidPushRequest(packageName, oid, oid, false);
        Deploy.InstallerRequest.Builder request = buildRequest("overlayidpush");
        request.setOverlayIdPush(overlayIdPushRequest);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.OverlayIdPushResponse response = installerResponse.getOverlayidpushResponse();
        logger.verbose("installer overlayidpush: " + response.getStatus().toString());
        return response;
    }

    private static Deploy.OverlayIdPush createOidPushRequest(
            String packageName, String prevOid, String nextOid, boolean wipeOverlays) {
        return Deploy.OverlayIdPush.newBuilder()
                .setPackageName(packageName)
                .setPrevOid(prevOid)
                .setNextOid(nextOid)
                .setWipeOverlays(wipeOverlays)
                .build();
    }

    @Override
    public Deploy.DeltaPreinstallResponse deltaPreinstall(Deploy.InstallInfo info)
            throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("deltapreinstall");
        request.setInstallInfoRequest(info);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.DeltaPreinstallResponse response = installerResponse.getDeltapreinstallResponse();
        logger.verbose("installer deltapreinstall: " + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.DeltaInstallResponse deltaInstall(Deploy.InstallInfo info) throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("deltainstall");
        request.setInstallInfoRequest(info);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.DeltaInstallResponse response = installerResponse.getDeltainstallResponse();
        logger.verbose("installer deltainstall: " + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.LiveLiteralUpdateResponse updateLiveLiterals(
            Deploy.LiveLiteralUpdateRequest liveLiterals) throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("liveliteralupdate");
        request.setLiveLiteralRequest(liveLiterals);
        Deploy.InstallerResponse installerResponse = sendInstallerRequest(request.build());
        Deploy.LiveLiteralUpdateResponse response = installerResponse.getLiveLiteralResponse();
        logger.verbose("installer liveliteralupdate: " + response.getStatus().toString());
        return response;
    }

    private Deploy.InstallerResponse sendInstallerRequest(Deploy.InstallerRequest request)
            throws IOException {
        return sendInstallerRequest(request, AdbClient.DEFAULT_TIMEOUT, AdbClient.DEFAULT_TIMEUNIT);
    }

    private Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOut, TimeUnit timeUnit) throws IOException {
        Trace.begin("./installer " + request.getCommandName());
        long start = System.nanoTime();
        Deploy.InstallerResponse response =
                sendInstallerRequest(request, OnFail.RETRY, timeOut, timeUnit);
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
    private Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest installerRequest,
            OnFail onFail,
            long timeOut,
            TimeUnit timeUnit)
            throws IOException {
        ByteBuffer request = wrap(installerRequest);
        Deploy.InstallerResponse response = null;

        long timeOutMs = timeUnit.toMillis(timeOut);
        AdbInstallerChannel channel = channelsProvider.getChannel(adb, getVersion());

        channel.lock();
        try {
            if (writeRequest(channel, request, timeOutMs)) {
                response = readResponse(channel, timeOutMs);
            }
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
            return sendInstallerRequest(installerRequest, OnFail.DO_NO_RETRY, timeOut, timeUnit);
        }

        // Parse response.
        if (response.getStatus() == Deploy.InstallerResponse.Status.ERROR_WRONG_VERSION) {
            if (onFail == OnFail.DO_NO_RETRY) {
                // This is the second time this error happens. Aborting.
                throw new IOException("Unrecoverable installer WRONG_VERSION error. Aborting");
            }
            channelsProvider.reset(adb);
            prepare();
            return sendInstallerRequest(installerRequest, OnFail.DO_NO_RETRY, timeOut, timeUnit);
        }

        if (mode == Mode.ONE_SHOT) {
            channelsProvider.reset(adb);
        }

        return response;
    }

    private boolean writeRequest(AdbInstallerChannel channel, ByteBuffer request, long timeOutMs) {
        try {
            channel.write(request, timeOutMs);
        } catch (IOException e) {
            // If the connection has been broken an IOException 'broken pipe' will be received here.
            return false;
        }
        return request.remaining() == 0;
    }

    private Deploy.InstallerResponse readResponse(AdbInstallerChannel channel, long timeOutMs) {
        try {
            ByteBuffer bufferMarker = ByteBuffer.allocate(MAGIC_NUMBER.length);
            channel.read(bufferMarker, timeOutMs);

            if (!Arrays.equals(MAGIC_NUMBER, bufferMarker.array())) {
                String garbage = new String(bufferMarker.array(), Charsets.UTF_8);
                logger.info("Read '" + garbage + "' from socket");
                return null;
            }

            ByteBuffer bufferSize =
                    ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(bufferSize, timeOutMs);
            int responseSize = bufferSize.getInt();
            if (responseSize < 0) {
                return null;
            }

            ByteBuffer bufferPayload = ByteBuffer.allocate(responseSize);
            channel.read(bufferPayload, timeOutMs);
            return unwrap(bufferPayload);
        } catch (IOException e) {
            // If the connection has been broken an IOException 'broken pipe' will be received here.
            logger.warning("Error while reading InstallerChannel");
            return null;
        }
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
                });
        runShell(
                new String[] {
                    "mkdir", "-p", Deployer.INSTALLER_DIRECTORY, Deployer.INSTALLER_TMP_DIRECTORY
                });

        // No need to check result here. If something wrong happens, an IOException is thrown.
        adb.push(installerFile.getAbsolutePath(), INSTALLER_PATH);

        runShell(new String[] {"chmod", "+x", INSTALLER_PATH});
    }

    private void runShell(String[] cmd) throws IOException {
        byte[] response = adb.shell(cmd);

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

    private Deploy.InstallerResponse unwrap(ByteBuffer buffer) {
        buffer.rewind();
        try {
            CodedInputStream cis = CodedInputStream.newInstance(buffer);
            return Deploy.InstallerResponse.parser().parseFrom(cis);
        } catch (IOException e) {
            // All in-memory buffers, should not happen
            throw new IllegalStateException(e);
        }
    }

    private ByteBuffer wrap(Deploy.InstallerRequest message) {
        int messageSize = message.getSerializedSize();
        int headerSize = MAGIC_NUMBER.length + Integer.BYTES;
        byte[] buffer = new byte[headerSize + messageSize];

        // Write size in the buffer.
        ByteBuffer headerWriter = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        headerWriter.put(MAGIC_NUMBER);
        headerWriter.putInt(messageSize);

        // Write protobuffer payload in the buffer.
        try {
            CodedOutputStream cos = CodedOutputStream.newInstance(buffer, headerSize, messageSize);
            message.writeTo(cos);
        } catch (IOException e) {
            // In memory buffers, should not happen
            throw new IllegalStateException(e);
        }
        return ByteBuffer.wrap(buffer);
    }

    @VisibleForTesting
    public String getVersion() {
        return Version.hash();
    }

    private Deploy.InstallerRequest.Builder buildRequest(String commandName) {
        Deploy.InstallerRequest.Builder request =
                Deploy.InstallerRequest.newBuilder()
                        .setCommandName(commandName)
                        .setVersion(getVersion());
        return request;
    }
}
