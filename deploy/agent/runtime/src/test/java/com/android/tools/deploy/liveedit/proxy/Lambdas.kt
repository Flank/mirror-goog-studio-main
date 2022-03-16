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
package com.android.tools.deploy.liveedit

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

suspend fun doSomethingUsefulOne(): Int {
    delay(200L) // pretend we are doing something useful here
    return 13
}

suspend fun doSomethingUsefulTwo(): Int {
    delay(200L) // pretend we are doing something useful here, too
    return 29
}

fun simple(): Sequence<Int> = sequence {
    for (i in 1..3) {
        yield(i)
    }
}

fun testRestrictedSuspend() : Int {
    var result = 0
    runBlocking<Unit> {
        for (i in simple()) {
            result = result + i
        }
    }
    return result
}

fun testSuspend() : Int {
    var result = 0
    runBlocking<Unit> {
        val one = doSomethingUsefulOne()
        val two = doSomethingUsefulTwo()
        result = one + two
    }
    return result
}

fun testAsyncAwait() = runBlocking<Int> {
    val one = async { doSomethingUsefulOne() }
    val two = async { doSomethingUsefulTwo() }
    one.await() + two.await()
}

fun testLaunchJoin() = runBlocking<Int> {
    var value = 0
    val job = launch { // launch a new coroutine and keep a reference to its Job
        delay(500L)
        value = 100
    }
    job.join()
    value
}