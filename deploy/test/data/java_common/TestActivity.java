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
package app;

import android.app.Activity;
import android.app.ActivityThread;
import pkg.KotlinCompanionTarget;
import pkg.KotlinFailedTarget;
import pkg.KotlinSimpleTarget;

public class TestActivity extends Activity {

    private static int counter = 0;

    public static void resetCounter() {
        counter = 0;
    }

    public static void incrementCounter() {
        counter++;
    }

    public static StaticVarInit.BgThread staticVarInit = null;

    public static void updateAppInfo() {
        ActivityThread.currentActivityThread()
                .getApplicationThread()
                .scheduleApplicationInfoChanged(null);
    }

    public static void printCounter() {
        System.out.println("TestActivity.counter = " + counter);
    }

    public TestActivity() {
        super("HotSwap Test Activity");
        resetCounter();
    }

    public void getStatus() {
        System.out.println(Target.getStatus());
    }

    public void getClassInitializerStatus() {
        System.out.println(new ClinitTarget().getStatus());
    }

    public void getFailedTargetStatus() {
        System.out.println(FailedTarget.getStatus());
    }

    public void getKotlinSimpleTargetStatus() {
        System.out.println(new KotlinSimpleTarget().getStatus());
    }

    public void getKotlinFailedTargetStatus() {
        System.out.println(new KotlinFailedTarget().getStatus());
    }

    public void getKotlinCompanionTargetStatus() {
        System.out.println(new KotlinCompanionTarget().getStatus());
    }

    public void getLambdaTargetStatus() {
        System.out.println(new LambdaTarget().getStatus());
    }

    public void getLambdaFailedTargetStatus() {
        System.out.println(new LambdaFailedTarget().getStatus());
    }

    public void getComposeTargetStatus() {
        System.out.println(new ComposeTarget().getStatus());
    }

    public void getNewClassStatus() {
        System.out.println(
                "public=" + new Wrapper().getPublic() + "package=" + new Wrapper().getPackage());
    }

    public void startBackgroundThread() throws Exception {
        staticVarInit = new StaticVarInit.BgThread();
        System.out.println(staticVarInit.startThread());
    }

    public void waitBackgroundThread() throws Exception {
        System.out.println(staticVarInit.waitThread());
    }

    public void fakeCrash() throws Exception {
        // Simulate the app crashing by calling the uncaught exception handler. We can't actually
        // throw a runtime exception without breaking FakeAndroid.
        Thread.class
                .getMethod("dispatchUncaughtException", Throwable.class)
                .invoke(Thread.currentThread(), new RuntimeException("crash"));
    }

    public void getStaticFinalPrimitives() {
        byte b = -1;
        int i = -1;
        char c = '\0';
        long l = -1l;
        short s = -1;
        float f = -1.0f;
        double d = -1.0d;
        boolean z = false;

        try {
            i =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_INT")
                            .getInt(StaticVarInit.AddStaticFinalPrimitives.class);
            b =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_BYTE")
                            .getByte(StaticVarInit.AddStaticFinalPrimitives.class);
            c =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_CHAR")
                            .getChar(StaticVarInit.AddStaticFinalPrimitives.class);
            l =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_LONG")
                            .getLong(StaticVarInit.AddStaticFinalPrimitives.class);
            s =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_SHORT")
                            .getShort(StaticVarInit.AddStaticFinalPrimitives.class);
            f =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_FLOAT")
                            .getFloat(StaticVarInit.AddStaticFinalPrimitives.class);
            d =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_DOUBLE")
                            .getDouble(StaticVarInit.AddStaticFinalPrimitives.class);
            z =
                    StaticVarInit.AddStaticFinalPrimitives.class
                            .getDeclaredField("X_BOOLEAN")
                            .getBoolean(StaticVarInit.AddStaticFinalPrimitives.class);
        } catch (IllegalAccessException e) {
            System.out.println(
                    "IllegalAccessException on StaticVarInit.AddStaticFinalPrimitives.X_INT");
            return;
        } catch (NoSuchFieldException e) {
            System.out.println(
                    "NoSuchFieldException on StaticVarInit.AddStaticFinalPrimitives.X_INT");
            return;
        }

        System.out.println(
                "StaticVarInit.X = "
                        + b
                        + " "
                        + i
                        + " "
                        + c
                        + " "
                        + l
                        + " "
                        + s
                        + " "
                        + f
                        + " "
                        + d
                        + " "
                        + z);
    }

    public void getStaticIntFromVirtual() {
        System.out.println("getStaticIntFromVirtual = " + new StaticVarInit().virtualGetY());
    }

    public void getVirtualsResult() {
        System.out.println("getVirtualsResult = " + Virtuals.getResult());
    }
}
