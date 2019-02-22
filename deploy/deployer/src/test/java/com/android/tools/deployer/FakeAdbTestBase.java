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
package com.android.tools.deployer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.utils.ILogger;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;

public abstract class FakeAdbTestBase {
    protected static final String BASE = "tools/base/deploy/deployer/src/test/resource/";

    protected final FakeDevice device;
    protected FakeAdbServer myAdbServer;
    protected ILogger logger;

    protected FakeAdbTestBase(FakeDevice device) {
        this.device = device;
    }

    @Before
    public void setup() throws Exception {
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.setHostCommandHandler(
                TrackDevicesCommandHandler.COMMAND, TrackDevicesCommandHandler::new);
        builder.addDeviceHandler(new FakeDeviceHandler(device));

        myAdbServer = builder.build();
        device.connectTo(myAdbServer);
        myAdbServer.start();
        logger = new TestLogger();
        AndroidDebugBridge.enableFakeAdbServerMode(myAdbServer.getPort());
    }

    @After
    public void teardown() throws Exception {
        AndroidDebugBridge.terminate();
        myAdbServer.close();
    }

    protected AndroidDebugBridge initDebugBridge() {
        AndroidDebugBridge.init(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return bridge;
    }

    private static class TestLogger implements ILogger {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> infos = new ArrayList<>();
        List<String> verboses = new ArrayList<>();

        @Override
        public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
            errors.add(String.format(msgFormat, args));
        }

        @Override
        public void warning(@NonNull String msgFormat, Object... args) {
            warnings.add(String.format(msgFormat, args));
        }

        @Override
        public void info(@NonNull String msgFormat, Object... args) {
            infos.add(String.format(msgFormat, args));
        }

        @Override
        public void verbose(@NonNull String msgFormat, Object... args) {
            verboses.add(String.format(msgFormat, args));
        }
    }

    protected static class EmptyUiService implements UIService {

        @Override
        public boolean prompt(String result) {
            return false;
        }

        @Override
        public void message(String message) {}
    }
}
