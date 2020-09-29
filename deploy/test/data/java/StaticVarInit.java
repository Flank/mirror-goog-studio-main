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
        // public static final int X_INT = 99; // Will be added in swap.
    }

    public static class RemoveStaticFinalPrimitives {
        public static final int X_INT = 99; // Will be removed in swap.
    }

    // Will be added in swap.
    // public static int Y = 89;

    // Will be added in swap.
    // public static int staticGetY() {
    //    return Y;
    //}

    public int virtualGetY() {
        return -89; // Will be changed to staticGetY();
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
            // counter2.countDown(); // Step 3
            // counter3.await(); // Step 6
            return "Not Waiting Yet";
        }
    }
}
