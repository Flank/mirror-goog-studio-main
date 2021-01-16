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
package app;

import androidx.compose.runtime.internal.LiveLiteralInfo;

public final class LiveLiteralOffsetLookupKt {

    /**
     * This function minicks what the compose compiler generates for each Live Literal Variable.
     *
     * <p>The function returns the default value of such variable. More importantly, the annotated
     * LiveLiteralInfo contains a offset number that the device will be used to look up the
     * variable's name.
     *
     * <p>For more information, refer to documentation of LiveLiteralInfo in JetPack Compose.
     */
    @LiveLiteralInfo(key = "Int_func_foo_bar_LiveLiteral_variable", offset = 159)
    private static int Int_func_foo_bar_LiveLiteral_variable() {
        return 1;
    }

    @LiveLiteralInfo(key = "key1", offset = 10001)
    private static String String_func_foo_bar_LiveLiteral_variable_key1() {
        return "value0";
    }

    @LiveLiteralInfo(key = "key2", offset = 10002)
    private static byte Byte_func_foo_bar_LiveLiteral_variable_key2() {
        return 0;
    }

    @LiveLiteralInfo(key = "key3", offset = 10003)
    private static char Char_func_foo_bar_LiveLiteral_variable_key3() {
        return '0';
    }

    @LiveLiteralInfo(key = "key4", offset = 10004)
    private static long Long_func_foo_bar_LiveLiteral_variable_key4() {
        return 0;
    }

    @LiveLiteralInfo(key = "key5", offset = 10005)
    private static short Short_func_foo_bar_LiveLiteral_variable_key5() {
        return 0;
    }

    @LiveLiteralInfo(key = "key6", offset = 10006)
    private static float Float_func_foo_bar_LiveLiteral_variable_key6() {
        return 0f;
    }

    @LiveLiteralInfo(key = "key7", offset = 10007)
    private static double Double_func_foo_bar_LiveLiteral_variable_key7() {
        return 0d;
    }

    @LiveLiteralInfo(key = "key8", offset = 10008)
    private static boolean Boolean_func_foo_bar_LiveLiteral_variable_key8() {
        return false;
    }
}
