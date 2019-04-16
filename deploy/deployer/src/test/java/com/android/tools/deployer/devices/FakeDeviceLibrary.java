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
import com.android.tools.deployer.devices.shell.Chmod;
import com.android.tools.deployer.devices.shell.Cmd;
import com.android.tools.deployer.devices.shell.GetProp;
import com.android.tools.deployer.devices.shell.Id;
import com.android.tools.deployer.devices.shell.Mkdir;
import com.android.tools.deployer.devices.shell.Rm;
import com.android.tools.deployer.devices.shell.RunAs;
import com.android.tools.deployer.devices.shell.SessionPm;

public class FakeDeviceLibrary {

    public enum DeviceId {
        API_19,
        API_21,
        API_22,
        API_23,
        API_24,
        API_25,
        API_26,
        API_27,
        API_28,
    }

    public FakeDevice build(DeviceId id) {
        FakeDevice device = null;
        switch (id) {
            case API_19:
                {
                    device = new FakeDevice("4.4", 19);
                    device.getShell().addCommand(new BasicPm());
                    break;
                }
            case API_21:
                {
                    device = new FakeDevice("5.0", 21);
                    device.getShell().addCommand(new SessionPm());
                    break;
                }
            case API_22:
                {
                    device = new FakeDevice("5.1", 22);
                    device.getShell().addCommand(new SessionPm());
                    break;
                }
            case API_23:
                {
                    device = new FakeDevice("6.0", 23);
                    device.getShell().addCommand(new SessionPm());
                    break;
                }
            case API_24:
                {
                    device = new FakeDevice("7.0", 24);
                    device.getShell().addCommand(new Cmd(false));
                    break;
                }
            case API_25:
                {
                    device = new FakeDevice("7.1", 25);
                    device.getShell().addCommand(new Cmd());
                    break;
                }
            case API_26:
                {
                    device = new FakeDevice("8.0", 26);
                    device.getShell().addCommand(new Cmd());
                    break;
                }
            case API_27:
                {
                    device = new FakeDevice("8.1", 27);
                    device.getShell().addCommand(new Cmd());
                    break;
                }
            case API_28:
                {
                    device = new FakeDevice("9.0", 28);
                    device.getShell().addCommand(new Cmd());
                    break;
                }
        }
        if (device != null) {
            device.getShell().addCommand(new GetProp());
            device.getShell().addCommand(new Mkdir());
            device.getShell().addCommand(new Chmod());
            device.getShell().addCommand(new Rm());
            device.getShell().addCommand(new RunAs());
            device.getShell().addCommand(new Id());
        }

        return device;
    }
}
