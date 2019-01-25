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
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.collect.ObjectArrays;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.io.ByteArrayInputStream;
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
import java.util.List;

public class AdbInstaller implements Installer {
    public static final String INSTALLER_BINARY_NAME = "installer";
    public static final String INSTALLER_PATH =
            Deployer.INSTALLER_DIRECTORY + "/" + INSTALLER_BINARY_NAME;
    public static final String ANDROID_EXECUTABLE_PATH = "/tools/base/deploy/installer/android";
    private final AdbClient adb;
    private final String installersFolder;
    private final ILogger logger;

    private enum OnFail {
        RETRY,
        DO_NO_RETRY
    };

    private static final int HEX_LINE_SIZE = 10;

    /**
     * The on-device binary facade.
     *
     * @param path a path to a directory with all the per-abi android executables.
     * @param adb the {@code AdbClient} to use.
     */
    public AdbInstaller(AdbClient adb, ILogger logger) {
        this(null, adb, logger);
    }

    public AdbInstaller(String installersFolder, AdbClient adb, ILogger logger) {
        this.adb = adb;
        this.installersFolder = installersFolder;
        this.logger = logger;
    }

    private void logEvents(List<Deploy.Event> events) {
        for (Deploy.Event event : events) {
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
    public Deploy.DumpResponse dump(List<String> packageNames) throws IOException {
        String[] cmd =
                buildCmd(
                        ObjectArrays.concat(
                                "dump", packageNames.toArray(new String[packageNames.size()])));
        Deploy.InstallerResponse installerResponse = invokeRemoteCommand(cmd, null);
        Deploy.DumpResponse response = installerResponse.getDumpResponse();
        logger.info("Dump response:" + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.SwapResponse swap(Deploy.SwapRequest request) throws IOException {
        String[] cmd = buildCmd(new String[] {"swap"});
        ByteArrayInputStream inputStream = wrap(request);
        Deploy.InstallerResponse installerResponse = invokeRemoteCommand(cmd, inputStream);
        Deploy.SwapResponse response = installerResponse.getSwapResponse();
        logger.info("Swap response:" + response.getStatus().toString());
        return response;
    }

    @Override
    public Deploy.DeltaPreinstallResponse deltaPreinstall(Deploy.DeltaPreinstallRequest request)
            throws IOException {
        String[] cmd = buildCmd(new String[] {"deltapreinstall"});
        ByteArrayInputStream inputStream = wrap(request);
        Deploy.InstallerResponse installerResponse = invokeRemoteCommand(cmd, inputStream);
        Deploy.DeltaPreinstallResponse response = installerResponse.getDeltapreinstallResponse();
        logger.info("DeltaPush response:" + response.getStatus().toString());
        return response;
    }

    private Deploy.InstallerResponse invokeRemoteCommand(
            String[] cmd, ByteArrayInputStream inputStream) throws IOException {
        Trace.begin("./installer " + cmd[2]);
        long start = System.nanoTime();
        Deploy.InstallerResponse response = invokeRemoteCommand(cmd, inputStream, OnFail.RETRY);
        logEvents(response.getEventsList());
        long end = System.nanoTime();

        long maxNs = Long.MIN_VALUE;
        long minNs = Long.MAX_VALUE;
        for (Deploy.Event event : response.getEventsList()) {
            maxNs = Math.max(maxNs, event.getTimestampNs());
            minNs = Math.min(minNs, event.getTimestampNs());
        }
        long delta = ((maxNs + minNs) - (end + start)) / 2;
        for (Deploy.Event event : response.getEventsList()) {
            switch (event.getType()) {
                case TRC_BEG:
                    Trace.begin(
                            event.getPid(),
                            event.getTid(),
                            event.getTimestampNs() - delta,
                            event.getText());
                    break;
                case TRC_END:
                    Trace.end(event.getPid(), event.getTid(), event.getTimestampNs() - delta);
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
    private Deploy.InstallerResponse invokeRemoteCommand(
            String[] cmd, ByteArrayInputStream inputStream, OnFail onFail) throws IOException {
        byte[] output = adb.shell(cmd, inputStream);
        Deploy.InstallerResponse response = unwrap(output, Deploy.InstallerResponse.parser());

        // Handle the case where the executable is not present on the device. In this case, the
        // shell invocation will return something that is not parsable by protobuffer. Most
        // likely something like "Command not found.".
        if (response == null) {
            printHexEditorStyle(output);
            if (onFail == OnFail.DO_NO_RETRY) {
                // This is the second time this error happens. Aborting.
                throw new IOException("Invalid installer response");
            }
            prepare();
            inputStream.reset();
            return invokeRemoteCommand(cmd, inputStream, OnFail.DO_NO_RETRY);
        }

        // Parse response.
        if (response.getStatus() == Deploy.InstallerResponse.Status.ERROR_WRONG_VERSION) {
            prepare();
            return invokeRemoteCommand(cmd, inputStream, OnFail.DO_NO_RETRY);
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

        adb.shell(new String[] {"mkdir", "-p", Deployer.INSTALLER_DIRECTORY}, null);
        adb.push(installerFile.getAbsolutePath(), INSTALLER_PATH);
        adb.shell(new String[] {"chmod", "+x", INSTALLER_PATH}, null);

        installerFile.delete();
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

    private String[] buildCmd(String[] parameters) {
        String[] base = {INSTALLER_PATH, "-version=" + Version.hash()};
        return ObjectArrays.concat(base, parameters, String.class);
    }

    private <T extends MessageLite> T unwrap(byte[] b, Parser<T> parser) {
        if (b == null) {
            return null;
        }
        if (b.length < Integer.BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        int size = buffer.getInt();
        if (size != buffer.remaining()) {
            return null;
        }
        try {
            CodedInputStream cis =
                    CodedInputStream.newInstance(b, Integer.BYTES, b.length - Integer.BYTES);
            return parser.parseFrom(cis);
        } catch (IOException e) {
            // All in-memory buffers, should not happen
            throw new IllegalStateException(e);
        }
    }

    private ByteArrayInputStream wrap(MessageLite message) {
        int size = message.getSerializedSize();
        byte[] buffer = new byte[Integer.BYTES + size];

        // Write size in the buffer.
        ByteBuffer sizeWritter = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        sizeWritter.putInt(size);

        // Write protobuffer payload in the buffer.
        try {
            CodedOutputStream cos = CodedOutputStream.newInstance(buffer, Integer.BYTES, size);
            message.writeTo(cos);
        } catch (IOException e) {
            // In memory buffers, should not happen
            throw new IllegalStateException(e);
        }
        return new ByteArrayInputStream(buffer);
    }

    private char printableChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (!Character.isISOControl(c)
                && block != null
                && block != Character.UnicodeBlock.SPECIALS) {
            return c;
        } else {
            return '.';
        }
    }

    // Print an hex editor styled line from a buffer. Example (with line size = 10):
    // [Ox22,Ox10,Ox53,Ox74,Ox61,Ox72,Ox74,Ox69,Ox6E,Ox67] ".Starting
    private void printHexEditorStyleLine(byte[] buffer, int offset) {
        // First print the hex version of the line Something line:
        // [Ox22,Ox10,Ox53,Ox74,Ox61,Ox72,Ox74,Ox69,Ox6E,Ox67]
        System.out.print("[");
        int i = 0;
        for (; offset + i < buffer.length && i < HEX_LINE_SIZE; i++) {
            System.out.print(String.format("Ox%02X", buffer[offset + i]));
            if (i < HEX_LINE_SIZE - 1) {
                System.out.print(",");
            }
        }
        // Pad line if smaller than line size.
        for (; i < HEX_LINE_SIZE; i++) {
            System.out.print("    ");
            if (i < HEX_LINE_SIZE - 1) {
                System.out.print(" ");
            }
        }
        System.out.print("] ");

        // Now write the ASCII version of the line. Something like:
        // ..Starting
        i = 0;
        for (i = 0; offset + i < buffer.length && i < HEX_LINE_SIZE; i++) {
            System.out.print(printableChar((char) buffer[offset + i]));
        }
        System.out.println();
    }

    private void printHexEditorStyle(byte[] buffer) {
        logger.info("Hex dump of buffer: " + buffer.length + " bytes.");
        for (int line = 0; line < buffer.length / HEX_LINE_SIZE; line++) {
            printHexEditorStyleLine(buffer, line * HEX_LINE_SIZE);
        }
        if (buffer.length % HEX_LINE_SIZE != 0) {
            printHexEditorStyleLine(buffer, (buffer.length / HEX_LINE_SIZE + 1) * HEX_LINE_SIZE);
        }
    }
}
