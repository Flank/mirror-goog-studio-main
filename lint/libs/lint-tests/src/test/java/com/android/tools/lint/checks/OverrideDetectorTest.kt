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
            base64gzip(
                "bin/classes/pkg1/Class1.class",
                "" +
                    "yv66vgAAADIAHgcAAgEAC3BrZzEvQ2xhc3MxBwAEAQAQamF2YS9sYW5nL09i" +
                    "amVjdAEABjxpbml0PgEAAygpVgEABENvZGUKAAMACQwABQAGAQAPTGluZU51" +
                    "bWJlclRhYmxlAQASTG9jYWxWYXJpYWJsZVRhYmxlAQAEdGhpcwEADUxwa2cx" +
                    "L0NsYXNzMTsBAAZtZXRob2QBAAdtZXRob2QyAQAEKEkpVgEAA2ZvbwEAAUkB" +
                    "AAdtZXRob2QzAQAHbWV0aG9kNAEAB21ldGhvZDUBAAdtZXRob2Q2AQAHbWV0" +
                    "aG9kNwEAClNvdXJjZUZpbGUBAAtDbGFzczEuamF2YQEADElubmVyQ2xhc3Nl" +
                    "cwcAHAEAEnBrZzEvQ2xhc3MxJENsYXNzNAEABkNsYXNzNAAhAAEAAwAAAAAA" +
                    "CAABAAUABgABAAcAAAAvAAEAAQAAAAUqtwAIsQAAAAIACgAAAAYAAQAAAAMA" +
                    "CwAAAAwAAQAAAAUADAANAAAAAAAOAAYAAQAHAAAAKwAAAAEAAAABsQAAAAIA" +
                    "CgAAAAYAAQAAAAUACwAAAAwAAQAAAAEADAANAAAAAAAPABAAAQAHAAAANQAA" +
                    "AAIAAAABsQAAAAIACgAAAAYAAQAAAAgACwAAABYAAgAAAAEADAANAAAAAAAB" +
                    "ABEAEgABAAAAEwAGAAEABwAAACsAAAABAAAAAbEAAAACAAoAAAAGAAEAAAAL" +
                    "AAsAAAAMAAEAAAABAAwADQAAAAAAFAAGAAEABwAAACsAAAABAAAAAbEAAAAC" +
                    "AAoAAAAGAAEAAAAOAAsAAAAMAAEAAAABAAwADQAAAAAAFQAGAAEABwAAACsA" +
                    "AAABAAAAAbEAAAACAAoAAAAGAAEAAAARAAsAAAAMAAEAAAABAAwADQAAAAAA" +
                    "FgAGAAEABwAAACsAAAABAAAAAbEAAAACAAoAAAAGAAEAAAAUAAsAAAAMAAEA" +
                    "AAABAAwADQAAAAAAFwAGAAEABwAAACsAAAABAAAAAbEAAAACAAoAAAAGAAEA" +
                    "AAAXAAsAAAAMAAEAAAABAAwADQAAAAIAGAAAAAIAGQAaAAAACgABABsAAQAd" +
                    "AAk="
            ),
            base64gzip(
                "bin/classes/pkg1/Class1\$Class4.class",
                "" +
                    "yv66vgAAADIAEwcAAgEAEnBrZzEvQ2xhc3MxJENsYXNzNAcABAEAC3BrZzEv" +
                    "Q2xhc3MxAQAGPGluaXQ+AQADKClWAQAEQ29kZQoAAwAJDAAFAAYBAA9MaW5l" +
                    "TnVtYmVyVGFibGUBABJMb2NhbFZhcmlhYmxlVGFibGUBAAR0aGlzAQAUTHBr" +
                    "ZzEvQ2xhc3MxJENsYXNzNDsBAAZtZXRob2QBAApTb3VyY2VGaWxlAQALQ2xh" +
                    "c3MxLmphdmEBAAxJbm5lckNsYXNzZXMBAAZDbGFzczQAIQABAAMAAAAAAAIA" +
                    "AQAFAAYAAQAHAAAALwABAAEAAAAFKrcACLEAAAACAAoAAAAGAAEAAAAZAAsA" +
                    "AAAMAAEAAAAFAAwADQAAAAAADgAGAAEABwAAACsAAAABAAAAAbEAAAACAAoA" +
                    "AAAGAAEAAAAbAAsAAAAMAAEAAAABAAwADQAAAAIADwAAAAIAEAARAAAACgAB" +
                    "AAEAAwASAAk="
            ),
            base64gzip(
                "bin/classes/pkg2/Class2.class",
                "" +
                    "yv66vgAAADIAIgcAAgEAC3BrZzIvQ2xhc3MyBwAEAQALcGtnMS9DbGFzczEB" +
                    "AAY8aW5pdD4BAAMoKVYBAARDb2RlCgADAAkMAAUABgEAD0xpbmVOdW1iZXJU" +
                    "YWJsZQEAEkxvY2FsVmFyaWFibGVUYWJsZQEABHRoaXMBAA1McGtnMi9DbGFz" +
                    "czI7AQAGbWV0aG9kAQAHbWV0aG9kMgEAFShMamF2YS9sYW5nL1N0cmluZzsp" +
                    "VgEAA2ZvbwEAEkxqYXZhL2xhbmcvU3RyaW5nOwEAB21ldGhvZDQBAAdtZXRo" +
                    "b2QzAQAHbWV0aG9kNQEAB21ldGhvZDYBAAdtZXRob2Q3AQAbUnVudGltZUlu" +
                    "dmlzaWJsZUFubm90YXRpb25zAQAhTGFuZHJvaWQvYW5ub3RhdGlvbi9TdXBw" +
                    "cmVzc0xpbnQ7AQAFdmFsdWUBAA5EYWx2aWtPdmVycmlkZQEAClNvdXJjZUZp" +
                    "bGUBAAtDbGFzczIuamF2YQEADElubmVyQ2xhc3NlcwcAIAEAEnBrZzIvQ2xh" +
                    "c3MyJENsYXNzMwEABkNsYXNzMwAhAAEAAwAAAAAACAABAAUABgABAAcAAAAv" +
                    "AAEAAQAAAAUqtwAIsQAAAAIACgAAAAYAAQAAAAYACwAAAAwAAQAAAAUADAAN" +
                    "AAAAAAAOAAYAAQAHAAAAKwAAAAEAAAABsQAAAAIACgAAAAYAAQAAAAgACwAA" +
                    "AAwAAQAAAAEADAANAAAAAAAPABAAAQAHAAAANQAAAAIAAAABsQAAAAIACgAA" +
                    "AAYAAQAAAAsACwAAABYAAgAAAAEADAANAAAAAAABABEAEgABAAgAEwAGAAEA" +
                    "BwAAACEAAAAAAAAAAbEAAAACAAoAAAAGAAEAAAAOAAsAAAACAAAAAgAUAAYA" +
                    "AQAHAAAAKwAAAAEAAAABsQAAAAIACgAAAAYAAQAAABEACwAAAAwAAQAAAAEA" +
                    "DAANAAAABAAVAAYAAQAHAAAAKwAAAAEAAAABsQAAAAIACgAAAAYAAQAAABQA" +
                    "CwAAAAwAAQAAAAEADAANAAAAAQAWAAYAAQAHAAAAKwAAAAEAAAABsQAAAAIA" +
                    "CgAAAAYAAQAAABcACwAAAAwAAQAAAAEADAANAAAAAQAXAAYAAgAYAAAADgAB" +
                    "ABkAAQAaWwABcwAbAAcAAAArAAAAAQAAAAGxAAAAAgAKAAAABgABAAAAGwAL" +
                    "AAAADAABAAAAAQAMAA0AAAACABwAAAACAB0AHgAAAAoAAQAfAAEAIQAB"
            ),
            base64gzip(
                "bin/classes/pkg2/Class2\$Class3.class",
                "" +
                    "yv66vgAAADIAGgcAAgEAEnBrZzIvQ2xhc3MyJENsYXNzMwcABAEAEGphdmEv" +
                    "bGFuZy9PYmplY3QBAAZ0aGlzJDABAA1McGtnMi9DbGFzczI7AQAGPGluaXQ+" +
                    "AQAQKExwa2cyL0NsYXNzMjspVgEABENvZGUJAAEACwwABQAGCgADAA0MAAcA" +
                    "DgEAAygpVgEAD0xpbmVOdW1iZXJUYWJsZQEAEkxvY2FsVmFyaWFibGVUYWJs" +
                    "ZQEABHRoaXMBABRMcGtnMi9DbGFzczIkQ2xhc3MzOwEABm1ldGhvZAEAClNv" +
                    "dXJjZUZpbGUBAAtDbGFzczIuamF2YQEADElubmVyQ2xhc3NlcwcAGAEAC3Br" +
                    "ZzIvQ2xhc3MyAQAGQ2xhc3MzACEAAQADAAAAARAQAAUABgAAAAIAAQAHAAgA" +
                    "AQAJAAAANAACAAIAAAAKKiu1AAoqtwAMsQAAAAIADwAAAAYAAQAAAB0AEAAA" +
                    "AAwAAQAAAAoAEQASAAAAAAATAA4AAQAJAAAAKwAAAAEAAAABsQAAAAIADwAA" +
                    "AAYAAQAAAB8AEAAAAAwAAQAAAAEAEQASAAAAAgAUAAAAAgAVABYAAAAKAAEA" +
                    "AQAXABkAAQ=="
            )
        ).run().expect(
            """
            src/pkg2/Class2.java:7: Error: This package private method may be unintentionally overriding method in pkg1.Class1 [DalvikOverride]
                void method() { // Flag this as an accidental override
                     ~~~~~~
                src/pkg1/Class1.java:4: This method is treated as overridden
            1 errors, 0 warnings
            """
        )
    }
}
