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

class OverrideDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return OverrideDetector()
    }

    fun test() {
        //noinspection all // Sample code
        lint().files(
            classpath(),
            manifest().minSdk(10),
            compiled(
                "bin/classes",
                java(
                    """
                    package pkg1;

                    public class Class1 {
                        void method() {
                        }

                        void method2(int foo) {
                        }

                        void method3() {
                        }

                        void method4() {
                        }

                        void method5() {
                        }

                        void method6() {
                        }

                        void method7() {
                        }

                        public static class Class4 extends Class1 {
                            void method() { // Not an error: same package
                            }
                        }
                    }
                    """
                ).indented(),
                0x832a33af,
                "pkg1/Class1.class:" +
                    "H4sIAAAAAAAAAIWQwU7CQBCG/4HSFloBEUVMPJh4QA82KMrFeCExIVE8aLy3" +
                    "sMFiaU1bfC9PJh58AB/KOLs1DYkmbTI7//z77XR3vr4/PgEMsVdDGTsGOgZ2" +
                    "DXQJ+ihwk2RAsMdhKGJViYT9Sz/00ytCuXf0SNBG0UwQGjd+KCarpSfiB9cL" +
                    "2NGXIn2KZgQjE6fM9sbyyK9xlqtBrs5zdZGrIaF2H63iqbj2ZWNLXaV/snBf" +
                    "Xa4mIklvhfxxYsOAydbL87zvZBShKTkncMO5c+ctxDQltNaAw+yZOECJByA/" +
                    "EyT78Frlap8zca4cv4PeWPBteNWVKQ9YOdpVu/iLVThsbKxhpf8wk6Ne3M3i" +
                    "aBRjdY5mMbapohBrc7SKsQ6PZEu9r41tzhq7Gu+BGalKPIvqD4aL/jJ0AgAA",
                "pkg1/Class1\$Class4.class:" +
                    "H4sIAAAAAAAAAGVOzQoBYRQ91zDDGL87yUJZYEFK2chGiZIN2Q++GD8zmm94" +
                    "Lytl4QE8lNz5RMqte8+5555O9/G83QF0kDOhwTKQMpAl6F3HdYIeQavW5oRo" +
                    "31sJQmbsuGJyOiyEP7MXe1b0gwg23opgTr2TvxQDJ1ST/b0tZauxtc82IT4R" +
                    "Mhh6MrAQRYyQP+7WrebbUlHQ5qAPsUauK3y1CclRP2aUEeEnw4qAwjCeOm8l" +
                    "RmKM1a+gCxOCwVNXYoE7/rUW1BX/tiInJ1SyiaRCDRlGk68hTyPxArsJjdos" +
                    "AQAA"
            ),
            compiled(
                "bin/classes",
                java(
                    """
                    package pkg2;

                    import android.annotation.SuppressLint;
                    import pkg1.Class1;

                    public class Class2 extends Class1 {
                        void method() { // Flag this as an accidental override
                        }

                        void method2(String foo) { // not an override: different signature
                        }

                        static void method4() { // not an override: static
                        }

                        private void method3() { // not an override: private
                        }

                        protected void method5() { // not an override: protected
                        }

                        public void method6() { // not an override: public
                        }

                        @SuppressLint("DalvikOverride")
                        public void method7() { // suppressed: no warning
                        }

                        public class Class3 extends Object {
                            void method() { // Not an override: not a subclass
                            }
                        }
                    }
                    """
                ).indented(),
                0xe8c9e67b,
                "pkg2/Class2\$Class3.class:" +
                    "H4sIAAAAAAAAAF1PTUvDQBScl8SkTbc21u+DiNJDrWCkHjwoXgqiUOpB8Z60" +
                    "S5uaJpLd+r88iODBH+CPEt9GherhvXlvZ3Zm9+Pz7R3AKbaqsLHiw0HTw6qH" +
                    "DYKrJ4lqHRPq/ceHcTfspZFS3TMmzpMs0ReEoP2HObgnOL18JAmNfpLJwXwW" +
                    "y+IuilM+cWdST/IRwW4bnX+bz4uhvEwMV/s2OJpGTxGhMpBKX+VKe9gUWIIr" +
                    "4EEQmgtZrRJO2PZ3ENdZJotyk4qfZrzCNMrG4U08lUPNKQv3scffdfjnFAQm" +
                    "gicLxDkV7lXe9nm3GP3O4Quo8wrr2ajhc3cZgR2uGsSPfrtkuf7LdjmoXtov" +
                    "o1FigHVjzKzN8xroCzPOvxKFAQAA",
                "pkg2/Class2.class:" +
                    "H4sIAAAAAAAAAIVQTUvDQBB9W9umtrVatVatCoIH9WCwfvSgCKIIhVrBihdP" +
                    "q1nq2nRTskn+lyfBgz/AHyVONhIUxS7szJuZt7Nv5v3j9Q1AC2tFTGDZQsPC" +
                    "ioVVhvyZy7XeYyi3lRK+iYSm/LFUMjhhmNjcumXInnmOYJjuSCW64fBe+Df8" +
                    "3qVMfiiCR89hsBLQZKhtdp54xG2Xq77dC3yp+kdxjy/Gfor2UnSQosMUtRga" +
                    "16EK5FC0VSS1pP9OlfICHkhPkcT1DleO70nH5mna7oWjkS+0JqHBEUMu4m5I" +
                    "Mivn3I3k4CoSvi/jSYo9L/QfxIWMhyiZsZs7sWyKukIHlyIeUpdhoUCp0aDf" +
                    "tBNWEu0m0S7D7LfaRrJNrCNDe45PASxuQXaSolXyjHxu+wXsmQAJIZs3yTzd" +
                    "UkpdMlX8phXoljH1jZb5ixZ3qvzohr9oFXo9Pf7TKrKYGU+bJ1sdT6uTnSVa" +
                    "5n9aA3NGITN9a3dgGgu017qZedE8zFIla1ZXNCiDHNgnGw9xae8CAAA="
            ),
        ).run().expect(
            """
            src/pkg2/Class2.java:7: Error: This package private method may be unintentionally overriding method in pkg1.Class1 [DalvikOverride]
                void method() { // Flag this as an accidental override
                     ~~~~~~
                src/pkg1/Class1.java:4: This method is treated as overridden
                void method() {
                     ~~~~~~
            1 errors, 0 warnings
            """
        )
    }
}
