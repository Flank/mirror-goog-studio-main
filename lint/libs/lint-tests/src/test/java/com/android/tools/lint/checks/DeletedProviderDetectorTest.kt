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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class DeletedProviderDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return DeletedProviderDetector()
    }

    fun testScenario() {
        lint().files(
            java(
                """
                package test.pkg;

                import javax.crypto.Cipher;
                import javax.crypto.KeyGenerator;
                import javax.crypto.NoSuchPaddingException;
                import javax.crypto.SecretKey;
                import javax.crypto.spec.IvParameterSpec;
                import java.security.NoSuchAlgorithmException;
                import java.security.NoSuchProviderException;
                import java.security.SecureRandom;

                @SuppressWarnings({"FieldCanBeLocal", "EmptyCatchBlock", "ClassNameDiffersFromFileName", "TryWithIdenticalCatches"})
                public class RemovedGeneratorTest {
                    private static byte[] f3341b;
                    private static byte[] f3340a;
                    private static SecretKey f3342c;
                    private static IvParameterSpec f3343d;
                    private static Cipher f3344e;
                    static {
                        System.loadLibrary("NewSecretJNI");
                        if (f3340a != null && f3341b != null) {
                            try {
                                KeyGenerator instance = KeyGenerator.getInstance("AES");
                                SecureRandom instance2 = SecureRandom.getInstance("SHA1PRNG", "Crypto");
                                instance2.setSeed(f3340a);
                                instance.init(128, instance2);
                                f3342c = instance.generateKey();
                                f3343d = new IvParameterSpec(f3341b);
                                f3344e = Cipher.getInstance("AES/CBC/PKCS5Padding");
                            } catch (NoSuchAlgorithmException e) {
                            } catch (NoSuchPaddingException e2) {
                            } catch (NoSuchProviderException e3) {
                                e3.printStackTrace();
                            }
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/RemovedGeneratorTest.java:24: Error: The Crypto provider has been deleted in Android P (and was deprecated in Android N), so the code will crash [DeletedProvider]
                            SecureRandom instance2 = SecureRandom.getInstance("SHA1PRNG", "Crypto");
                                                                                          ~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }
}
