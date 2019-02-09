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

import com.android.tools.deployer.devices.shell.BasicPm;
import com.android.tools.deployer.devices.shell.Cmd;
import com.android.tools.deployer.devices.shell.GetProp;
import com.android.tools.deployer.devices.shell.SessionPm;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class FakeDeviceLibrary {

    ImmutableList<FakeDevice> devices;

    public FakeDeviceLibrary() {
        ImmutableList.Builder<FakeDevice> builder = ImmutableList.builder();

        FakeDevice device = new FakeDevice("4.4", "19");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new BasicPm());
        builder.add(device);

        device = new FakeDevice("5.0", "21");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new SessionPm());
        builder.add(device);

        device = new FakeDevice("5.1", "22");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new SessionPm());
        builder.add(device);

        device = new FakeDevice("6.0", "23");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new SessionPm());
        builder.add(device);

        device = new FakeDevice("7.0", "24");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new Cmd(false));
        builder.add(device);

        device = new FakeDevice("7.1", "25");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new Cmd());
        builder.add(device);

        device = new FakeDevice("8.0", "26");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new Cmd());
        builder.add(device);

        device = new FakeDevice("8.1", "27");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new Cmd());
        builder.add(device);

        device = new FakeDevice("9.0", "28");
        device.getShell().addComand(new GetProp());
        device.getShell().addComand(new Cmd());
        builder.add(device);

        devices = builder.build();
    }

    public List<FakeDevice> getDevices() {
        return devices;
    }
}
