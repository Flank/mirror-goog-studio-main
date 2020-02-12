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

package com.android.testutils

/**
 * Base class to write given/expect test using lambdas configuring the [GivenT] and [ResulT]
 * objects.
 *
 * A good use case is when the input and the result are multiple objects and you want to compare
 * them in a single test. Using basic POJOs for both inputs and results allows you to use [given]
 * and [expect] as a DSL to configure them.
 *
 * Use with:
 *
 * given {
 *     a = 1
 *     b = 2
 * }
 *
 * `when` {
 *     Result(
 *        sum = it.a + it.b,
 *        subtraction = it.a - it.b
 *     )
 *  }
 *
 *  expect {
 *     sum = 3
 *     subtraction = -1
 *  }
 */
abstract class AbstractBuildGivenBuildExpectTest<GivenT, ResultT> :
    AbstractGivenExpectTest<GivenT, ResultT>() {

    private var givenAction: (GivenT.() -> Unit)? = null

    /**
     * Registers an action block returning the given state as a single object
     */
    protected open fun given(action: GivenT.() -> Unit) {
        checkState(TestState.START)
        givenAction = action
        state = TestState.GIVEN
    }

    /**
     * Registers an action block return the expected result values. This also runs the test.
     */
    protected fun expect(expectedProvider: ResultT.() -> Unit) {
        val given = instantiateGiven().also {
            givenAction?.invoke(it) ?: throw RuntimeException("No given data")
        }
        runTest(
            given,
            instantiateResult().also {
                initResultDefaults(given, it)
                expectedProvider.invoke(it)
            }
        )
    }

    /**
     * pre-process the result with the given, before passing it to the expect action
     */
    protected open fun initResultDefaults(given: GivenT, result: ResultT) {
        // do nothing
    }

    abstract fun instantiateGiven(): GivenT
    abstract fun instantiateResult(): ResultT
}
