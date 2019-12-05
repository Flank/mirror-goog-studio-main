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

import com.android.apksig.ApkSigner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
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

public class SignerConfig {
    private final String algo;
    private final String subType;

    private final PrivateKey privateKey;
    private final List<X509Certificate> certificates;

    public SignerConfig(Workspace workspace, String algoName, String subName) {
        this.algo = algoName;
        this.subType = subName;
        try {
            PrivateKey privateKey =
                    toPrivateKey(algoName + "-" + subName + ".pk8", algoName, workspace);
            List<X509Certificate> certs =
                    toCertificateChain(algoName + "-" + subName + ".x509.pem", workspace);
            ApkSigner.SignerConfig signerConfig =
                    new ApkSigner.SignerConfig.Builder(algoName, privateKey, certs).build();
            this.privateKey = signerConfig.getPrivateKey();
            this.certificates = signerConfig.getCertificates();
        } catch (IOException
                | CertificateException
                | NoSuchAlgorithmException
                | InvalidKeySpecException e) {
            String error =
                    String.format("Unable to build signerConfig from '%s'-'%s'", algo, subType);
            throw new IllegalStateException(error, e);
        }
    }

    public PrivateKey toPrivateKey(String resourceName, String keyAlgorithm, Workspace workspace)
            throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(workspace.getResourcePath(resourceName));

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

    public List<X509Certificate> toCertificateChain(String resourceName, Workspace workspace)
            throws IOException, CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        byte[] bytes = Files.readAllBytes(workspace.getResourcePath(resourceName));
        Collection<? extends Certificate> certs =
                certificateFactory.generateCertificates(new ByteArrayInputStream(bytes));
        List<X509Certificate> result = new ArrayList<>(certs.size());
        for (Certificate cert : certs) {
            result.add((X509Certificate) cert);
        }
        return result;
    }

    public String getAlgo() {
        return algo;
    }

    public String getSubType() {
        return subType;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public List<X509Certificate> getCertificates() {
        return certificates;
    }

}
