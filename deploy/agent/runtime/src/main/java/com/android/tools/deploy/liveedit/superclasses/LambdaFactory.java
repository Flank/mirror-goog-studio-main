/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import kotlin.coroutines.Continuation;

public final class LambdaFactory {

    public static Object create(String superInternalName, Object[] args, Object proxy) {
        switch (superInternalName) {
            case "kotlin/jvm/internal/Lambda":
                return makeLambda(args);
            case "kotlin/coroutines/jvm/internal/SuspendLambda":
                return makeSuspendLambda(args, proxy);
            case "kotlin/coroutines/jvm/internal/RestrictedSuspendLambda":
                return makeRestrictedSuspendLambda(args, proxy);
            case "java/lang/Object":
                return null;
            default:
                throw new IllegalArgumentException("Unhandled superclass: " + superInternalName);
        }
    }

    private static Object makeLambda(Object args[]) {
        return new LiveEditLambda((int) args[0]);
    }

    private static Object makeSuspendLambda(Object args[], Object proxy) {
        return new LiveEditSuspendLambda((int) args[0], (Continuation) args[1], proxy);
    }

    private static Object makeRestrictedSuspendLambda(Object args[], Object proxy) {
        return new LiveEditRestrictedSuspendLambda((int) args[0], (Continuation) args[1], proxy);
    }
}
