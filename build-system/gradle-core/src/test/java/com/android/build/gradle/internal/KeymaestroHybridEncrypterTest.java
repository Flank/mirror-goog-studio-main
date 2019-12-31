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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECPoint;
import org.junit.Test;

/** Unit tests for {@link KeymaestroHybridEncrypter}. */
public class KeymaestroHybridEncrypterTest {
    public byte[] fromHex(String string) {
        BigInteger bigInt = new BigInteger(string, 16);
        byte[] result = new byte[string.length() / 2];
        KeymaestroHybridEncrypter.fitBigInteger(bigInt, result, 0, result.length);
        return result;
    }

    public String toHex(byte[] hex) {
        return new BigInteger(1 /* positive */, hex).toString(16);
    }

    @Test
    public void hkdf() throws GeneralSecurityException {
        // https://tools.ietf.org/html/rfc5869#appendix-A
        {
            byte[] ikm = fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
            byte[] salt = fromHex("000102030405060708090a0b0c");
            byte[] info = fromHex("f0f1f2f3f4f5f6f7f8f9");
            String okmHex =
                    "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887"
                            + "185865";

            byte[] result = KeymaestroHybridEncrypter.hkdf(ikm, salt, info, okmHex.length() / 2);
            assertThat(toHex(result)).isEqualTo(okmHex);
        }
        {
            byte[] ikm =
                    fromHex(
                            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20212223242526"
                                    + "2728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f");
            byte[] salt =
                    fromHex(
                            "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f80818283848586"
                                    + "8788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
            byte[] info =
                    fromHex(
                            "b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6"
                                    + "d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");
            String okmHex =
                    "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827"
                            + "271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87";

            byte[] result = KeymaestroHybridEncrypter.hkdf(ikm, salt, info, okmHex.length() / 2);
            assertThat(toHex(result)).isEqualTo(okmHex);
        }
    }

    @Test
    public void ecdh() throws GeneralSecurityException {
        // RFC 5114 section A.6.
        // https://tools.ietf.org/html/rfc5114.html
        {
            String privateKeyHex =
                    "814264145f2f56f2e96a8e337a1284993faf432a5abce59e867b7291d507a3af";
            String publicKeyHex =
                    "b120de4aa36492795346e8de6c2c8646ae06aaea279fa775b3ab0715f6ce51b09f1b7eece20d7b5"
                            + "ed8ec685fa3f071d83727027092a8411385c34dde5708b2b6";
            String resultHex = "dd0f5396219d1ea393310412d19a08f1f5811e9dc8ec8eea7f80d21c820c2788";

            ECPrivateKey privateKey =
                    KeymaestroHybridEncrypter.deserializePrivateKey(fromHex(privateKeyHex));
            ECPoint publicKey = KeymaestroHybridEncrypter.deserializePoint(fromHex(publicKeyHex));
            byte[] exchange = KeymaestroHybridEncrypter.ecdh(privateKey, publicKey);

            assertThat(toHex(exchange)).isEqualTo(resultHex);
        }
        {
            String privateKeyHex =
                    "2ce1788ec197e096db95a200cc0ab26a19ce6bccad562b8eee1b593761cf7f41";
            String publicKeyHex =
                    "2af502f3be8952f2c9b5a8d4160d09e97165be50bc42ae4a5e8d3b4ba83aeb15eb0faf4ca986c4d"
                            + "38681a0f9872d79d56795bd4bff6e6de3c0f5015ece5efd85";
            String resultHex = "dd0f5396219d1ea393310412d19a08f1f5811e9dc8ec8eea7f80d21c820c2788";

            ECPrivateKey privateKey =
                    KeymaestroHybridEncrypter.deserializePrivateKey(fromHex(privateKeyHex));
            ECPoint publicKey = KeymaestroHybridEncrypter.deserializePoint(fromHex(publicKeyHex));
            byte[] exchange = KeymaestroHybridEncrypter.ecdh(privateKey, publicKey);

            assertThat(toHex(exchange)).isEqualTo(resultHex);
        }
    }

    @Test
    public void encrypt() throws GeneralSecurityException {
        // Just make sure we don't throw an exception.
        String keyHashHex = "11223344";
        String publicKeyHex =
                "3ccc4e49bc00feda426f21d8c21633375e460d8eed2864db71ae28f73a4ff3738eb37263573fadf"
                        + "75007098581f36a9433ac21a9ebdf0549c0fed043e7097e2f";

        byte[] key = fromHex(keyHashHex + publicKeyHex);
        KeymaestroHybridEncrypter encrypter = new KeymaestroHybridEncrypter(key);

        byte[] plaintext = "banana".getBytes(UTF_8);
        encrypter.encrypt(plaintext);

        plaintext = "".getBytes(UTF_8);
        encrypter.encrypt(plaintext);

        plaintext = new byte[16 * 1024];
        encrypter.encrypt(plaintext);
    }

    @Test
    public void badKey() throws GeneralSecurityException {
        try {
            byte[] key = null;
            new KeymaestroHybridEncrypter(key);
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageThat().contains("publicKey is null");
        }

        try {
            byte[] key = new byte[KeymaestroHybridEncrypter.CONSTRUCTOR_KEY_LENGTH - 1];
            new KeymaestroHybridEncrypter(key);
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected)
                    .hasMessageThat()
                    .contains(
                            "publicKey should be "
                                    + KeymaestroHybridEncrypter.CONSTRUCTOR_KEY_LENGTH
                                    + " bytes");
        }

        try {
            byte[] key = new byte[KeymaestroHybridEncrypter.CONSTRUCTOR_KEY_LENGTH + 1];
            new KeymaestroHybridEncrypter(key);
            fail();
        } catch (IllegalArgumentException expected) {
            assertThat(expected)
                    .hasMessageThat()
                    .contains(
                            "publicKey should be "
                                    + KeymaestroHybridEncrypter.CONSTRUCTOR_KEY_LENGTH
                                    + " bytes");
        }

        try {
            String keyHashHex = "11223344";
            String publicKeyHex =
                    "3ccc4e49bc00feda426f21d8c21633375e460d8eed2864db71ae28f73a4ff3738eb37263573fadf"
                            + "75007098581f36a9433ac21a9ebdf0549c0fed043e7097e2e";
            byte[] key = fromHex(keyHashHex + publicKeyHex);
            new KeymaestroHybridEncrypter(key);
            fail();
        } catch (GeneralSecurityException expected) {
            assertThat(expected).hasMessageThat().contains("point is not on the curve");
        }
    }
}
