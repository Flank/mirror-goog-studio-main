/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.builder.internal.packaging

import com.android.testutils.TestResources
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

fun getPrivateKey(): PrivateKey {
    val bytes =
            Files.readAllBytes(TestResources.getFile("/testData/packaging/rsa-2048.pk8").toPath())
    return KeyFactory.getInstance("rsa").generatePrivate(PKCS8EncodedKeySpec(bytes))
}

fun getCertificates(): List<X509Certificate> {
    val bytes =
            Files.readAllBytes(
                    TestResources.getFile("/testData/packaging/rsa-2048.x509.pem").toPath()
            )
    return CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(bytes))
            .map { it as X509Certificate }
}
