/*
 * Copyright (C) 2012 The Android Open Source Project
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

class SecureRandomDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SecureRandomDetector()
    }

    fun testSeed() {
        val expected =
            """
            src/test/pkg/SecureRandomTest.java:12: Warning: It is dangerous to seed SecureRandom with the current time because that value is more predictable to an attacker than the default seed [SecureRandom]
                    random1.setSeed(System.currentTimeMillis()); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:13: Warning: It is dangerous to seed SecureRandom with the current time because that value is more predictable to an attacker than the default seed [SecureRandom]
                    random1.setSeed(System.nanoTime()); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:15: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random1.setSeed(0); // Wrong
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:16: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random1.setSeed(1); // Wrong
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:17: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random1.setSeed((int)1023); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:18: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random1.setSeed(1023L); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:19: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random1.setSeed(FIXED_SEED); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:29: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random3.setSeed(0); // Wrong: owner is java/util/Random, but applied to SecureRandom object
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:41: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random2.setSeed(seed); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:47: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random2.setSeed(seedBytes); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SecureRandomTest.java:55: Warning: Do not call setSeed() on a SecureRandom with a fixed seed: it is not secure. Use getSeed(). [SecureRandom]
                    random2.setSeed(fixedSeed); // Wrong
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 11 warnings
            """
        lint().files(
            java(
                "src/test/pkg/SecureRandomTest.java",
                """
                    package test.pkg;

                    import java.security.SecureRandom;
                    import java.util.Random;
                    @SuppressWarnings({"ClassNameDiffersFromFileName", "RedundantCast", "MethodMayBeStatic"})
                    public class SecureRandomTest {
                        private static final long FIXED_SEED = 1000L;
                        protected int getDynamicSeed() {  return 1; }

                        public void testLiterals() {
                            SecureRandom random1 = new SecureRandom();
                            random1.setSeed(System.currentTimeMillis()); // Wrong
                            random1.setSeed(System.nanoTime()); // Wrong
                            random1.setSeed(getDynamicSeed()); // OK
                            random1.setSeed(0); // Wrong
                            random1.setSeed(1); // Wrong
                            random1.setSeed((int)1023); // Wrong
                            random1.setSeed(1023L); // Wrong
                            random1.setSeed(FIXED_SEED); // Wrong
                        }

                        public static void testRandomTypeOk() {
                            Random random2 = new Random();
                            random2.setSeed(0); // OK
                        }

                        public static void testRandomTypeWrong() {
                            Random random3 = new SecureRandom();
                            random3.setSeed(0); // Wrong: owner is java/util/Random, but applied to SecureRandom object
                        }

                        public static void testBytesOk() {
                            SecureRandom random1 = new SecureRandom();
                            byte[] seed = random1.generateSeed(4);
                            random1.setSeed(seed); // OK
                        }

                        public static void testBytesWrong() {
                            SecureRandom random2 = new SecureRandom();
                            byte[] seed = new byte[3];
                            random2.setSeed(seed); // Wrong
                        }

                        public static void testFixedSeedBytes(byte something) {
                            SecureRandom random2 = new SecureRandom();
                            byte[] seedBytes = new byte[] { 1, 2, 3 };
                            random2.setSeed(seedBytes); // Wrong
                            byte[] seedBytes2 = new byte[] { 1, something, 3 };
                            random2.setSeed(seedBytes2); // OK
                        }

                        private static final byte[] fixedSeed = new byte[] { 1, 2, 3 };
                        public void testFixedSeedBytesField() {
                            SecureRandom random2 = new SecureRandom();
                            random2.setSeed(fixedSeed); // Wrong
                        }

                    }
                    """
            ).indented()
        ).run().expect(expected)
    }
}
