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
import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.util.RunnablesExecutor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

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

    @NonNull final String name;

    @NonNull final PrivateKey privateKey;

    @NonNull final List<X509Certificate> certificates;

    final RunnablesExecutor executor;

    final boolean v1Enabled;
    final boolean v2Enabled;

    final boolean v3Enabled;
    final SigningCertificateLineage v3SigningCertificateLineage;

    final boolean v4Enabled;
    final File v4Output;

    final String v1CreatedBy;
    final String v1BuiltBy;
    final boolean v1TrustManifest;

    final int minSdkVersion;
    final byte[] sdkDependencies;

    private SignedApkOptions(
            String name,
            PrivateKey privateKey,
            List<X509Certificate> certificates,
            RunnablesExecutor executor,
            byte[] sdkDependencies,
            boolean v1Enabled,
            boolean v2Enabled,
            boolean v3Enabled,
            SigningCertificateLineage v3SigningCertificateLineage,
            boolean v4Enabled,
            File v4Output,
            String v1CreatedBy,
            String v1BuiltBy,
            boolean v1TrustManifest,
            int minSdkVersion) {
        this.name = name;
        this.privateKey = privateKey;
        this.certificates = certificates;
        this.executor = executor;
        this.sdkDependencies = sdkDependencies;
        this.v1Enabled = v1Enabled;
        this.v2Enabled = v2Enabled;
        this.v3Enabled = v3Enabled;
        this.v3SigningCertificateLineage = v3SigningCertificateLineage;
        this.v4Enabled = v4Enabled;
        this.v4Output = v4Output;
        this.v1CreatedBy = v1CreatedBy;
        this.v1BuiltBy = v1BuiltBy;
        this.v1TrustManifest = v1TrustManifest;
        this.minSdkVersion = minSdkVersion;
    }

    public static PrivateKey bytesToPrivateKey(String keyAlgorithm, byte[] bytes)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        // Keep overly strictly linter happy by limiting what JCA KeyFactory algorithms are used
        // here
        KeyFactory keyFactory;
        switch (keyAlgorithm.toUpperCase(Locale.US)) {
            case "RSA":
                keyFactory = KeyFactory.getInstance("rsa");
                break;
            case "DSA":
                keyFactory = KeyFactory.getInstance("dsa");
                break;
            case "EC":
                keyFactory = KeyFactory.getInstance("ec");
                break;
            default:
                throw new IllegalStateException("Unsupported key algorithm: " + keyAlgorithm);
        }

        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    public static List<X509Certificate> bytesToCertificateChain(byte[] bytes)
            throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs =
                certificateFactory.generateCertificates(new ByteArrayInputStream(bytes));
        List<X509Certificate> result = new ArrayList<>(certs.size());
        for (Certificate cert : certs) {
            result.add((X509Certificate) cert);
        }
        return result;
    }

    public static class Builder {
        String name = "CERT";
        PrivateKey privateKey;
        List<X509Certificate> certificates;
        RunnablesExecutor executor;
        byte[] sdkDependencies;
        boolean v1Enabled = false;
        boolean v2Enabled = true;
        boolean v3Enabled = false;
        SigningCertificateLineage v3SigningCertificateLineage;
        boolean v4Enabled = false;
        File v4Output = null;
        String v1CreatedBy = "Signflinger";
        String v1BuiltBy = "Signflinger";
        boolean v1TrustManifest;
        int minSdkVersion;

        public Builder setName(@NonNull String name) {
            this.name = name;
            return this;
        }

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

        public Builder setSdkDependencies(byte[] sdkDependencies) {
            this.sdkDependencies = sdkDependencies;
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

        public Builder setV3Enabled(boolean enabled) {
            this.v3Enabled = enabled;
            return this;
        }

        public Builder setV3SigningCertificateLineage(
                SigningCertificateLineage v3SigningCertificateLineage) {
            this.v3SigningCertificateLineage = v3SigningCertificateLineage;
            return this;
        }

        public Builder setV4Enabled(boolean enabled) {
            this.v4Enabled = enabled;
            return this;
        }

        public Builder setV4Output(File output) {
            this.v4Output = output;
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
                    name,
                    privateKey,
                    certificates,
                    executor,
                    sdkDependencies,
                    v1Enabled,
                    v2Enabled,
                    v3Enabled,
                    v3SigningCertificateLineage,
                    v4Enabled,
                    v4Output,
                    v1CreatedBy,
                    v1BuiltBy,
                    v1TrustManifest,
                    minSdkVersion);
        }
    }
}
