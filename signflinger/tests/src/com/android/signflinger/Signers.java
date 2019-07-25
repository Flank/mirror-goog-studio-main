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

public class Signers {
    static SignerConfig getDefaultRSA() throws Exception {
        int defaultRSAindex = 1;
        return Utils.getSignerConfig(
                signers[defaultRSAindex].type, signers[defaultRSAindex].subtype);
    }

    static Signer[] signers = {
        new Signer("rsa", "1024"), //  80-bit (obsolete)
        new Signer("rsa", "2048"), // 112-bit
        new Signer("rsa", "3072"), // 128-bit
        new Signer("rsa", "4096"),
        new Signer("rsa", "8192"),
        new Signer("rsa", "16384"),
        new Signer("dsa", "1024"),
        new Signer("dsa", "2048"),
        new Signer("dsa", "3072"),
        new Signer("ec", "p256"),
        new Signer("ec", "p384"),
        new Signer("ec", "p521"),
    };
}
