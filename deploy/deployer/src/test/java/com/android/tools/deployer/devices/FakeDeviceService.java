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
package com.android.tools.deployer.devices;

import com.android.tools.idea.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;

class FakeDeviceService extends FakeDeviceServiceGrpc.FakeDeviceServiceImplBase {

    private FakeDevice device;

    public FakeDeviceService(FakeDevice device) {
        this.device = device;
    }

    @Override
    public void getAppUid(
            Proto.GetAppUidRequest request,
            StreamObserver<Proto.GetAppUidResponse> responseObserver) {
        Proto.GetAppUidResponse.Builder builder = Proto.GetAppUidResponse.newBuilder();
        FakeDevice.Application app = device.getApplication(request.getPackage());
        if (app != null) {
            builder.setUid(app.user.uid);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void recordCommand(
            Proto.RecordCommandRequest request,
            StreamObserver<Proto.RecordCommandResponse> responseObserver) {
        device.getShell().getHistory().add(request.getCommand());
        responseObserver.onNext(Proto.RecordCommandResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<Proto.ShellCommand> executeCommand(
            StreamObserver<Proto.CommandResponse> response) {
        return new ExecutorStreamObserver(response);
    }

    class ExecutorStreamObserver implements StreamObserver<Proto.ShellCommand> {

        private final StreamObserver<Proto.CommandResponse> response;
        private final ArrayBlockingQueue<ByteString> pipe;
        private final InputStream stdin;
        private final OutputStream stdout;
        private Thread thread;

        public ExecutorStreamObserver(StreamObserver<Proto.CommandResponse> response) {
            this.response = response;
            this.pipe = new ArrayBlockingQueue<>(1);
            this.stdout =
                    new OutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            write(new byte[] {(byte) b}, 0, 1);
                        }

                        @Override
                        public void write(byte[] b, int off, int len) throws IOException {
                            final Proto.CommandResponse res =
                                    Proto.CommandResponse.newBuilder()
                                            .setStdout(ByteString.copyFrom(b, off, len))
                                            .build();
                            response.onNext(res);
                        }
                    };
            this.stdin =
                    new InputStream() {
                        private InputStream buffer = null;

                        @Override
                        public int read() throws IOException {
                            byte[] b = new byte[1];
                            int r = read(b, 0, 1);
                            return r == 1 ? b[0] : r;
                        }

                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            try {
                                int r = buffer == null ? -1 : buffer.read(b, off, len);
                                if (r <= 0) {
                                    buffer = pipe.take().newInput();
                                    r = buffer.read(b, off, len);
                                }
                                return r;
                            } catch (InterruptedException e) {
                                return 0;
                            }
                        }
                    };
        }

        @Override
        public void onNext(Proto.ShellCommand command) {
            if (!command.getCommand().isEmpty()) {
                thread =
                        new Thread(
                                () -> {
                                    try {
                                        FakeDevice.User user = device.getUser(command.getUid());
                                        int result =
                                                device.getShell()
                                                        .execute(
                                                                command.getCommand(),
                                                                user,
                                                                stdout,
                                                                stdin,
                                                                device);
                                        Proto.CommandResponse.Builder res =
                                                Proto.CommandResponse.newBuilder();
                                        res.setExitCode(result);
                                        res.setTerminate(true);
                                        response.onNext(res.build());
                                    } catch (IOException e) {
                                        response.onError(e);
                                    }
                                });
                thread.start();
            }
            final ByteString stdin = command.getStdin();
            if (!stdin.isEmpty()) {
                try {
                    pipe.put(stdin);
                } catch (InterruptedException e) {
                    response.onError(e);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace();
        }

        @Override
        public void onCompleted() {
            try {
                thread.join();
            } catch (InterruptedException e) {
            }
            // onCompleted does a full close, there is no way of just closing the server
            // writes and keep receiving messages.
            // If we call response.onCompleted before we receive all the messages,
            // then some messages will be lost. We use the terminate flag in the responses
            // to mark the last message.
            response.onCompleted();
        }
    }
}
