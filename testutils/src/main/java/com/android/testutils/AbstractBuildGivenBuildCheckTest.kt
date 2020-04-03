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

open abstract class AbstractBuildGivenBuildCheckTest<GivenT, ResultT> :
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
     * runs the tests and compares the results.
     */
    private fun runTest(given: GivenT): ResultT? {
        // run the states by running all the necessary actions.
        val actual = whenAction?.invoke(given) ?: defaultWhen(given)
        state = TestState.DONE
        return actual
    }

    /**
     * Registers an action block on the test result. This also runs the test.
     */
    protected open fun check(action: ResultT?.() -> Unit) {
        val given = instantiateGiven().also {
            givenAction?.invoke(it) ?: throw RuntimeException("No given data")
        }
        action.invoke(runTest(given))
    }

    abstract fun instantiateGiven(): GivenT
}
