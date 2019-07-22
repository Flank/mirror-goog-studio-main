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

import com.android.annotations.NonNull;
import com.android.apksig.util.RunnablesExecutor;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Options for SignedApk.
 *
 * <p>Private key size has an impact on performance. While 1024-bit keys are now obsolete,
 * 2048-bit/4096-bit seem to provide the best performance. 16000-bit signing is 10x slower than
 * 2048-bit. As the time of the writing of this lib, 2048-bit key are recommended.
 *
 * <p>Providing an executor speeds up considerably v2 signing. If not provided, apksig is
 * single-threaded
 */
public class SignedApkOptions {

    @NonNull final PrivateKey privateKey;

    @NonNull final List<X509Certificate> certificates;

    final RunnablesExecutor executor;

    final boolean v1Enabled;
    final boolean v2Enabled;

    final String v1CreatedBy;
    final String v1BuiltBy;
    final boolean v1TrustManifest;

    final int minSdkVersion;

    private SignedApkOptions(
            PrivateKey privateKey,
            List<X509Certificate> certificates,
            RunnablesExecutor executor,
            boolean v1Enabled,
            boolean v2Enabled,
            String v1CreatedBy,
            String v1BuiltBy,
            boolean v1TrustManifest,
            int minSdkVersion) {
        this.privateKey = privateKey;
        this.certificates = certificates;
        this.executor = executor;
        this.v1Enabled = v1Enabled;
        this.v2Enabled = v2Enabled;
        this.v1CreatedBy = v1CreatedBy;
        this.v1BuiltBy = v1BuiltBy;
        this.v1TrustManifest = v1TrustManifest;
        this.minSdkVersion = minSdkVersion;
    }

    public static class Builder {
        PrivateKey privateKey;
        List<X509Certificate> certificates;
        RunnablesExecutor executor;
        boolean v1Enabled = false;
        boolean v2Enabled = true;
        String v1CreatedBy = "Signflinger";
        String v1BuiltBy = "Signflinger";
        boolean v1TrustManifest;
        int minSdkVersion;

        public Builder setPrivateKey(@NonNull PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Builder setCertificates(@NonNull List<X509Certificate> certificates) {
            this.certificates = certificates;
            return this;
        }

        public Builder setExecutor(@NonNull RunnablesExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder setV1Enabled(boolean enabled) {
            this.v1Enabled = enabled;
            return this;
        }

        public Builder setV2Enabled(boolean enabled) {
            this.v2Enabled = enabled;
            return this;
        }

        public Builder setV1CreatedBy(@NonNull String creator) {
            v1CreatedBy = creator;
            return this;
        }

        public Builder setV1BuiltBy(@NonNull String builder) {
            v1BuiltBy = builder;
            return this;
        }

        public Builder setV1TrustManifest(boolean trust) {
            v1TrustManifest = trust;
            return this;
        }

        public Builder setMinSdkVersion(int minSdkVersion) {
            this.minSdkVersion = minSdkVersion;
            return this;
        }

        @NonNull
        public SignedApkOptions build() {
            return new SignedApkOptions(
                    privateKey,
                    certificates,
                    executor,
                    v1Enabled,
                    v2Enabled,
                    v1CreatedBy,
                    v1BuiltBy,
                    v1TrustManifest,
                    minSdkVersion);
        }
    }
}
