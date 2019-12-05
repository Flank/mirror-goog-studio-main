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

package com.android.signflinger;

import java.util.ArrayList;

public class Signers {
    public static SignerConfig[] getAll(Workspace workspace) {
        ArrayList<SignerConfig> signers = new ArrayList<>();
        signers.add(new SignerConfig(workspace, "rsa", "2048")); // 112-bit (default)
        signers.add(new SignerConfig(workspace, "rsa", "3072")); // 128-bit
        signers.add(new SignerConfig(workspace, "rsa", "4096"));
        signers.add(new SignerConfig(workspace, "rsa", "8192"));
        signers.add(new SignerConfig(workspace, "rsa", "16384"));
        signers.add(new SignerConfig(workspace, "dsa", "1024"));
        signers.add(new SignerConfig(workspace, "dsa", "2048"));
        signers.add(new SignerConfig(workspace, "dsa", "3072"));
        signers.add(new SignerConfig(workspace, "ec", "p256"));
        signers.add(new SignerConfig(workspace, "ec", "p384"));
        signers.add(new SignerConfig(workspace, "ec", "p521"));
        SignerConfig[] signersArray = new SignerConfig[signers.size()];
        signers.toArray(signersArray);
        return signersArray;
    }

    public static SignerConfig getDefaultRSASigner(Workspace workspace) {
        return new SignerConfig(workspace, "rsa", "2048");
    }
}
