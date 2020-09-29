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
package app;

import java.util.concurrent.CountDownLatch;

// TODO: These redefinition only works on final class right now.
public final class StaticVarInit {
    public static class AddStaticFinalPrimitives {
        public static final int X_INT = 99; // Added.
        public static final byte X_BYTE = 100; // Added.
        public static final char X_CHAR = '!'; // Added.
        public static final long X_LONG = 1000l; // Added.
        public static final short X_SHORT = 7; // Added.
        public static final float X_FLOAT = 3.14f; // Added.
        public static final double X_DOUBLE = 3e-100d; // Added.
        public static final boolean X_BOOLEAN = true; // Added.
    }

    public static class RemoveStaticFinalPrimitives {
        // public static final int X_INT = 99; // This will attempt to remove a primitive.
    }

    public static int Y = 89;

    public static int staticGetY() {
        return Y;
    }

    public int virtualGetY() {
        return staticGetY();
    }

    public static class BgThread {
        private final CountDownLatch counter1 = new CountDownLatch(1);
        private final CountDownLatch counter2 = new CountDownLatch(1);
        private final CountDownLatch counter3 = new CountDownLatch(1);

        public String startThread() throws InterruptedException {
            (new Thread() {
                        @Override
                        public void run() {
                            try {
                                counter1.countDown(); // Step 1
                                counter2.await(); // Step 4
                                counter3.countDown(); // Step 5
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .start();

            counter1.await(); // Step 2
            return "Background Thread Started";
        }

        public String waitThread() throws InterruptedException {
            counter2.countDown(); // Step 3
            counter3.await(); // Step 6
            return "Background Thread Finished";
        }
    }
}
