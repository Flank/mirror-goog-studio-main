/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deployer.model.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.TestLogger;
import com.android.tools.manifest.parser.XmlNode;
import com.android.tools.manifest.parser.components.ManifestAppComponentInfo;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class ComplicationTest {

    @Test
    public void testCommandSendToDevice()
            throws DeployerException, ShellCommandUnresponsiveException,
                    AdbCommandRejectedException, IOException, TimeoutException {
        IDevice device = Mockito.mock(IDevice.class);
        ManifestAppComponentInfo info =
                new ManifestAppComponentInfo(new XmlNode(), "com.example.myApp") {
                    @Override
                    public String getQualifiedName() {
                        return "com.example.services.Complication";
                    }
                };
        Complication complication =
                new Complication(info, "com.example.myApp", device, new TestLogger());
        complication.activate(
                "debug.app.watchface com.example.WatchFaces$InnerWatchFace 1 LONG_TEXT",
                AppComponent.Mode.RUN,
                new NullOutputReceiver());

        String expectedCommand =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication --ecn component 'com.example.myApp/com.example.services.Complication' --ecn watchface 'debug.app.watchface/com.example.WatchFaces\\$InnerWatchFace' --ei slot 1 --ei type 4";
        Mockito.verify(device, Mockito.times(1))
                .executeShellCommand(
                        eq(expectedCommand),
                        any(IShellOutputReceiver.class),
                        eq(15L),
                        eq(TimeUnit.SECONDS));
    }

    @Test
    public void testCommandSendToDeviceDebug()
            throws DeployerException, ShellCommandUnresponsiveException,
                    AdbCommandRejectedException, IOException, TimeoutException {
        IDevice device = Mockito.mock(IDevice.class);
        InOrder inOrderDevice = Mockito.inOrder(device);

        ManifestAppComponentInfo info =
                new ManifestAppComponentInfo(new XmlNode(), "com.example.myApp") {
                    @Override
                    public String getQualifiedName() {
                        return "com.example.services.Complication";
                    }
                };
        Complication complication =
                new Complication(info, "com.example.myApp", device, new TestLogger());
        complication.activate(
                "debug.app.watchface com.example.WatchFaces$InnerWatchFace 1 LONG_TEXT",
                AppComponent.Mode.DEBUG,
                new NullOutputReceiver());

        inOrderDevice
                .verify(device)
                .executeShellCommand(
                        eq("am set-debug-app -w 'com.example.myApp'"),
                        any(IShellOutputReceiver.class),
                        eq(15L),
                        eq(TimeUnit.SECONDS));

        String expectedCommand =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication --ecn component 'com.example.myApp/com.example.services.Complication' --ecn watchface 'debug.app.watchface/com.example.WatchFaces\\$InnerWatchFace' --ei slot 1 --ei type 4";

        inOrderDevice
                .verify(device)
                .executeShellCommand(
                        eq(expectedCommand),
                        any(IShellOutputReceiver.class),
                        eq(15L),
                        eq(TimeUnit.SECONDS));
    }
}
