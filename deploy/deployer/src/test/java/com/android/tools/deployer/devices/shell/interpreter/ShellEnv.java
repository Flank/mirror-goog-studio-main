/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.deployer.devices.shell.interpreter;

import com.android.annotations.NonNull;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.shell.ShellCommand;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ShellEnv implements AutoCloseable {
    private static final int PIPE_SIZE = 1024 * 1024;

    private FakeDevice device;
    private Map<String, ShellCommand> commands;
    private Map<String, String> scope;
    private InputStream inputStream;
    private PrintStream outputStream;
    private InputStream pipeMux; // Generalized input stream for the network, pipes, as well as null-input streams.
    private PipedInputStream pipeIn;
    private PipedOutputStream pipeOut;
    private PrintStream pipeOutPrintStream;
    private Path cwd;

    public ShellEnv(
            @NonNull FakeDevice device,
            @NonNull Map<String, ShellCommand> commands,
            @NonNull InputStream inputStream,
            @NonNull OutputStream outputStream)
            throws IOException {
        this.device = device;
        this.commands = commands;
        this.inputStream = inputStream;
        this.outputStream = new PrintStream(outputStream);
        pipeMux = inputStream; // Initialize the mux to the network input.
        scope = new HashMap<>();
        pipeIn = new PipedInputStream(PIPE_SIZE);
        pipeOut = new PipedOutputStream(pipeIn);
        pipeOutPrintStream = new PrintStream(pipeOut);
        cwd = Paths.get("/");
    }

    @Override
    public void close() throws Exception {
        pipeIn.close();
        pipeOut.close();
    }

    @NonNull
    public byte[] readAllBytesFromPipe() throws IOException {
        pipeOut.flush();
        int available = pipeIn.available();
        byte[] buffer = new byte[available];
        int totalRead = 0;
        int read = 0;
        while (read >= 0 && totalRead < available) {
            read = pipeIn.read(buffer, totalRead, buffer.length - totalRead);
            totalRead += read;
        }
        return buffer;
    }

    @NonNull
    public String readStringFromPipe() throws IOException {
        return new String(readAllBytesFromPipe(), Charsets.UTF_8);
    }

    /**
     * Prepares stdin to be the output of the previous command.
     */
    void preparePipe() {
        pipeMux = pipeIn;
    }

    /**
     * Consumes stdin pipe and sets the mux to an empty InputStream.
     * @return
     */
    @NonNull
    InputStream takeStdin() {
        InputStream pipe = pipeMux;
        pipeMux = new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        };
        return pipe;
    }

    @NonNull
    OutputStream getStdout() {
        return pipeOut;
    }

    @NonNull
    PrintStream getPrintStdout() {
        return pipeOutPrintStream;
    }

    @NonNull
    InputStream getInputStream() {
        return inputStream;
    }

    @NonNull
    PrintStream getOutputStream() {
        return outputStream;
    }

    @NonNull
    FakeDevice getDevice() {
        return device;
    }

    ShellCommand getCommand(@NonNull String commandName) {
        return commands.get(commandName);
    }

    void setScope(@NonNull String varName, String value) {
        // Removes the mapping if {@code value} is null.
        scope.compute(varName, (key, val) -> value);
    }

    @NonNull
    String getScope(@NonNull String varName) {
        return scope.getOrDefault(varName, ""); // Shell resolves to "" when env var isn't found.
    }

    @NonNull
    Path getCwd() {
        return cwd;
    }
}
