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

import java.lang.reflect.Proxy;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.RestrictedSuspendLambda;

public final class LiveEditRestrictedSuspendLambda extends RestrictedSuspendLambda {
    private final Object proxy;
    private final ProxyClassHandler handler;

    public LiveEditRestrictedSuspendLambda(int arity, Continuation completion, Object proxy) {
        super(arity, completion);
        this.proxy = proxy;
        this.handler = (ProxyClassHandler) Proxy.getInvocationHandler(proxy);
    }

    public Object invokeSuspend(Object object) {
        return handler.invokeMethod(
                proxy,
                "invokeSuspend",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                new Object[] {object});
    }
}
