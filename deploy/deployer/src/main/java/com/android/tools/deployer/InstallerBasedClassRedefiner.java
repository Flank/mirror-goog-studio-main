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

import com.android.tools.deploy.proto.Deploy.SwapRequest;
import com.android.tools.deploy.proto.Deploy.SwapResponse;
import java.io.IOException;

public class InstallerBasedClassRedefiner extends ClassRedefiner {
    private final Installer installer;

    public InstallerBasedClassRedefiner(Installer installer) {
        this.installer = installer;
    }

    @Override
    public SwapResponse redefine(SwapRequest request) throws DeployerException {
        try {
            return installer.swap(request);
        } catch (IOException e) {
            throw new DeployerException(DeployerException.Error.REDEFINER_ERROR, e);
        }
    }
}
