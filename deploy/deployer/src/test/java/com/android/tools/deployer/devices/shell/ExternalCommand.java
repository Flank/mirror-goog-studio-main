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
package com.android.tools.deployer.devices.shell;

import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.shell.interpreter.ShellContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExternalCommand extends ShellCommand {
    private final String executable;

    public ExternalCommand(String executable) {
        this.executable = executable;
    }

    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        FakeDevice device = context.getDevice();
        List<String> command = new ArrayList<>();
        File exe = new File(device.getStorage(), executable);
        command.add(exe.getAbsolutePath());
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        device.putEnv(context.getUser(), pb.environment());
        Process process = pb.start();
        PipeConnector inToProcess = new PipeConnector(stdin, process.getOutputStream());
        PipeConnector processToOut = new PipeConnector(process.getInputStream(), stdout);
        inToProcess.start();
        processToOut.start();
        int code = 255;
        try {
            code = process.waitFor();
            processToOut.join();
            stdin.close();
            inToProcess.join();
        } catch (InterruptedException e) {
        }
        return code;
    }

    private static class PipeConnector extends Thread {
        private final InputStream input;
        private final OutputStream output;

        private PipeConnector(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } catch (IOException e) {
                // Ignore and exit the thread
            }
        }
    }

    @Override
    public String getExecutable() {
        return executable;
    }
}
