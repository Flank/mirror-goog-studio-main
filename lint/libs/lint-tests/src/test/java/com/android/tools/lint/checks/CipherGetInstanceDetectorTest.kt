/*
 * Copyright (C) 2014 The Android Open Source Project
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

class CipherGetInstanceDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector {
        return CipherGetInstanceDetector()
    }

    fun testCipherGetInstanceAES() {
        val expected =
            """
            src/test/pkg/CipherGetInstanceAES.java:8: Warning: Cipher.getInstance should not be called without setting the encryption mode and padding [GetInstance]
                Cipher.getInstance("AES");
                                   ~~~~~
            0 errors, 1 warnings
            """

        lint().files(
            java(
                """
                package test.pkg;

                import javax.crypto.Cipher;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CipherGetInstanceAES {
                  private void foo() throws Exception {
                    Cipher.getInstance("AES");
                  }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testCipherGetInstanceDES() {
        val expected =
            """
            src/test/pkg/CipherGetInstanceDES.java:8: Warning: Cipher.getInstance should not be called without setting the encryption mode and padding [GetInstance]
                Cipher.getInstance("DES");
                                   ~~~~~
            0 errors, 1 warnings
            """

        lint().files(
            java(
                """
                package test.pkg;

                import javax.crypto.Cipher;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CipherGetInstanceDES {
                  private void foo() throws Exception {
                    Cipher.getInstance("DES");
                  }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testCipherGetInstanceAESECB() {
        val expected =
            """
            src/test/pkg/CipherGetInstanceAESECB.java:8: Warning: ECB encryption mode should not be used [GetInstance]
                Cipher.getInstance("AES/ECB/NoPadding");
                                   ~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """

        lint().files(
            java(
                """
                    package test.pkg;

                    import javax.crypto.Cipher;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class CipherGetInstanceAESECB {
                      private void foo() throws Exception {
                        Cipher.getInstance("AES/ECB/NoPadding");
                      }
                    }
                    """
            ).indented()
        ).run().expect(expected)
    }

    fun testCipherGetInstanceAESCBC() {

        lint().files(
            java(
                """
                package test.pkg;

                import javax.crypto.Cipher;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CipherGetInstanceAESCBC {
                  private void foo() throws Exception {
                    Cipher.getInstance("AES/CBC/NoPadding");
                  }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    // http://b.android.com/204099 Generate a warning only when ECB mode
    // is used with symmetric ciphers such as DES.
    fun testAsymmetricCipherRSA() {

        lint().files(
            java(
                """
                package test.pkg;

                import javax.crypto.Cipher;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CipherGetInstanceRSA {
                  private void foo() throws Exception {
                    Cipher.getInstance("RSA/ECB/NoPadding");
                  }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testResolveConstants() {
        val expected =
            """
            src/test/pkg/CipherGetInstanceTest.java:11: Warning: ECB encryption mode should not be used (was "DES/ECB/NoPadding") [GetInstance]
                    Cipher des = Cipher.getInstance(Constants.DES);
                                                    ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """

        lint().files(
            java(
                """
                package test.pkg;

                import java.security.NoSuchAlgorithmException;

                import javax.crypto.Cipher;
                import javax.crypto.NoSuchPaddingException;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class CipherGetInstanceTest {
                    public void test() throws NoSuchPaddingException, NoSuchAlgorithmException {
                        Cipher des = Cipher.getInstance(Constants.DES);
                    }

                    public static class Constants {
                        public static final String DES = "DES/ECB/NoPadding";
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testDeprecatedProvider() {
        val expected =
            """
                src/test/pkg/BCProviderTest.java:16: Warning: The BC provider is deprecated and when targetSdkVersion is moved to P this method will throw a NoSuchAlgorithmException. To fix this you should stop specifying a provider and use the default implementation [GetInstance]
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", "BC"); // Error
                                                                   ~~~~
                src/test/pkg/BCProviderTest.java:17: Warning: The BC provider is deprecated and when targetSdkVersion is moved to P this method will throw a NoSuchAlgorithmException. To fix this you should stop specifying a provider and use the default implementation [GetInstance]
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", BC_PROVIDER); // Error
                                                                   ~~~~~~~~~~~
                src/test/pkg/BCProviderTest.java:19: Warning: The BC provider is deprecated and when targetSdkVersion is moved to P this method will throw a NoSuchAlgorithmException. To fix this you should stop specifying a provider and use the default implementation [GetInstance]
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", Security.getProvider("BC")); // Error
                                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/BCProviderTest.java:20: Warning: The BC provider is deprecated and when targetSdkVersion is moved to P this method will throw a NoSuchAlgorithmException. To fix this you should stop specifying a provider and use the default implementation [GetInstance]
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", Security.getProvider(BC_PROVIDER)); // Error
                                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                    """
        lint().files(
            java(
                """
                package test.pkg;

                import javax.crypto.Cipher;
                import javax.crypto.NoSuchPaddingException;
                import java.security.NoSuchAlgorithmException;
                import java.security.NoSuchProviderException;
                import java.security.Security;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class BCProviderTest {
                    public static final String BC_PROVIDER = "BC";

                    void test() throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException {
                        Cipher.getInstance("AES/CBC/PKCS7PADDING");   // OK
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", "bar"); // OK
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", "BC"); // Error
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", BC_PROVIDER); // Error
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", Security.getProvider("bar")); // OK
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", Security.getProvider("BC")); // Error
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", Security.getProvider(BC_PROVIDER)); // Error
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testDeprecatedProviderPorHigher() {
        val expected =
            """
            src/test/pkg/BCProviderTest.java:8: Warning: The BC provider is deprecated and as of Android P this method will throw a NoSuchAlgorithmException. To fix this you should stop specifying a provider and use the default implementation [GetInstance]
                    Cipher.getInstance("AES/CBC/PKCS7PADDING", "BC"); // Error
                                                               ~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifest().targetSdk("P"),
            java(
                """
                package test.pkg;

                import javax.crypto.Cipher;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class BCProviderTest {
                    void test() throws Exception {
                        Cipher.getInstance("AES/CBC/PKCS7PADDING", "BC"); // Error
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }
}
