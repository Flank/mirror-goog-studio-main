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

import com.google.common.truth.Truth

/**
 * Abstract class allowing to write test using a given/expect DSL.
 *
 * Do no use Directly. Instead use [AbstractBuildGivenBuildExpectTest] or
 * [AbstractReturnGivenReturnExpectTest]
 */
abstract class AbstractGivenExpectTest<GivenT, ResultT> {

    private var givenAction: (() -> GivenT)? = null
    protected var whenAction: ((GivenT) -> ResultT?)? = null

    enum class TestState {
        START,
        GIVEN,
        WHEN,
        DONE
    }

    protected var state: TestState = TestState.START

    /**
     * Registers an action block converting the [given] object to a result object.
     *
     * This is to be used in tests where the action needs to be custom. If the action
     * is the same for all the tests of the class, then [defaultWhen] can be overridden instead.
     */
    fun `when`(action: (GivenT) -> ResultT?) {
        checkState(TestState.GIVEN)
        whenAction = action
        state = TestState.WHEN
    }

    /**
     * runs the tests and compares the results.
     */
    protected fun runTest(given: GivenT, expected: ResultT?) {
        // run the states by running all the necessary actions.
        val actual = whenAction?.invoke(given) ?: defaultWhen(given)
        compareResult(expected, actual, given)
        state = TestState.DONE
    }

    /**
     * This compares the actual result vs the expected result.
     *
     * Default implementation does assertThat.isEqual
     */
    open fun compareResult(expected: ResultT?, actual: ResultT?, given: GivenT) {
        Truth.assertThat(actual).isEqualTo(expected)
    }

    /**
     * A default implementation for the given -> result action
     */
    open fun defaultWhen(given: GivenT): ResultT? {
        throw RuntimeException("Test is using default implementation of defaultWhen")
    }

    /**
     * checks the current state of the test.
     */
    protected fun checkState(expectedState: TestState) {
        if (state != expectedState) {
            throw RuntimeException("Expected State is not $expectedState, it is $state")
        }
    }
}

/**
 * infix function to create Pair<>, similar to 'to'.
 * this can read better when GivenT is a pair.
 */
infix fun <A, B> A.on(that: B): Pair<A, B> = Pair(this, that)