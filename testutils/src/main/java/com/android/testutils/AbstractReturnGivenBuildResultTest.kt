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
 * Base class to write given/expect test using one returning the [GivenT] object and one to
 * build the [ResulT] object.
 *
 * A good use case is when the input and the result are a single objects
 *
 * Use with:
 *
 * given {
 *     10
 * }
 *
 * `when` {
 *     it.pow(2)
 *  }
 *
 *  expect {
 *     result = 100
 *  }
 */
abstract class AbstractReturnGivenBuildResultTest<GivenT, ResultBuilderT: AbstractReturnGivenBuildResultTest.ResultBuilder<ResultT>, ResultT> :
    AbstractGivenExpectTest<GivenT, ResultT>() {

    private var givenAction: (() -> GivenT)? = null

    /**
     * Registers an action block returning the given state as a single object
     */
    open fun given(action: () -> GivenT) {
        checkState(TestState.START)
        givenAction = action
        state = TestState.GIVEN
    }

    /**
     * Registers an action block return the expected result values. This also runs the test.
     */
    fun expect(resultAction: ResultBuilderT.() -> Unit) {
        runTest(
            givenAction?.invoke() ?: noGivenData(),
            instantiateResulBuilder().also { resultAction.invoke(it) }.toResult()
        )
    }

    open fun noGivenData(): GivenT {
        throw RuntimeException("No given data")
    }

    abstract fun instantiateResulBuilder(): ResultBuilderT

    interface ResultBuilder<T> {
        fun toResult(): T
    }
}
