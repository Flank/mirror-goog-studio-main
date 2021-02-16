/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl.decorator

import com.android.build.gradle.internal.dsl.AgpDslLockedException
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

internal class LockableListTest {

    @Test
    fun `check behaves as a list`() {
        val lockableList = LockableList<String>("someStrings")
        lockableList += "one"
        lockableList += "one"
        assertThat(lockableList).containsExactly("one", "one")
        lockableList -= "one"
        assertThat(lockableList).containsExactly("one")
    }

    @Test
    fun `check list locking addition`() {
        val lockableList = LockableList<String>("someStrings")
        lockableList += "zero"
        lockableList += "one"
        assertThat(lockableList).containsExactly("zero", "one")

        lockableList.lock()

        val failure = assertFailsWith<AgpDslLockedException> {
            lockableList += "two"
        }
        assertThat(failure).hasMessageThat().contains("It is too late to modify someStrings")
        assertThat(lockableList).containsExactly("zero", "one")
    }

    @Test
    fun `check list locking set`() {
        val lockableList = LockableList<String>("someStrings")
        lockableList += listOf("zero", "should be replaced by 'one'")
        lockableList[1] = "one"
        assertThat(lockableList).containsExactly("zero", "one")

        lockableList.lock()

        val failure = assertFailsWith<AgpDslLockedException> {
            lockableList[1] = "should not replace"
        }
        assertThat(failure).hasMessageThat().contains("It is too late to modify someStrings")
        assertThat(lockableList).containsExactly("zero", "one")
    }

    @Test
    fun `check list locking removal`() {
        val lockableList = LockableList<String>("someStrings")
        lockableList += listOf("zero", "one")
        assertThat(lockableList).containsExactly("zero", "one")
        // Check removal from iterator works before locking
        val result = lockableList.remove("zero")
        assertThat(result).isTrue()
        assertThat(lockableList).containsExactly("one")

        lockableList.lock()
        // Check that removal fails after locking
        val failure = assertFailsWith<AgpDslLockedException> {
            lockableList.remove("one")
        }
        assertThat(failure).hasMessageThat().contains("It is too late to modify someStrings")
        assertThat(lockableList).containsExactly("one")
    }


    @Test
    fun `check iterator locking removal`() {
        val lockableList = LockableList<String>("someStrings")
        lockableList += listOf("zero", "one")
        assertThat(lockableList).containsExactly("zero", "one")
        val iterator = lockableList.iterator()
        assertThat(iterator.next()).isEqualTo("zero")
        // Check removal from iterator works before locking
        iterator.remove()
        assertThat(lockableList).containsExactly("one")

        lockableList.lock()
        // Check that removal via iterator fails after locking
        assertThat(iterator.next()).isEqualTo("one")
        val failure = assertFailsWith<AgpDslLockedException> {
            iterator.remove()
        }
        assertThat(failure).hasMessageThat().contains("It is too late to modify someStrings")
        assertThat(lockableList).containsExactly("one")
    }
}
