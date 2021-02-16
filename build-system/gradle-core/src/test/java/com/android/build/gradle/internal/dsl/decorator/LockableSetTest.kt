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

internal class LockableSetTest {

    @Test
    fun `check behaves as a set`() {
        val lockableSet = LockableSet<String>("someStrings")
        lockableSet += "one"
        lockableSet += "one"
        assertThat(lockableSet).containsExactly("one")
        lockableSet -= "one"
        assertThat(lockableSet).isEmpty()
    }

    @Test
    fun `check order is preserved`() {
        val lockableSet = LockableSet<String>("someStrings")
        lockableSet += "one"
        lockableSet += "two"
        assertThat(lockableSet).containsExactly("one", "two").inOrder()

        val lockableSet2 = LockableSet<String>("someStrings2")
        lockableSet2 += "two"
        lockableSet2 += "one"
        assertThat(lockableSet2).containsExactly("two", "one").inOrder()
    }

    @Test
    fun `check set locking addition`() {
        val lockableSet = LockableSet<String>("someStrings")
        lockableSet += "one"
        lockableSet += "two"
        assertThat(lockableSet).containsExactly("one", "two")

        lockableSet.lock()

        val failure = assertFailsWith<AgpDslLockedException> {
            lockableSet += "three"
        }
        assertThat(failure).hasMessageThat().contains("It is too late to modify someStrings")
        assertThat(lockableSet).containsExactly("one", "two")
    }


    @Test
    fun `check set locking removal`() {
        val lockableSet = LockableSet<String>("someStrings")
        lockableSet += setOf("one", "two")
        assertThat(lockableSet).containsExactly("one", "two")
        val result = lockableSet.remove("two")
        assertThat(result).isTrue()
        assertThat(lockableSet).containsExactly("one")

        lockableSet.lock()

        val failure = assertFailsWith<AgpDslLockedException> {
            lockableSet.remove("one")
        }
        assertThat(failure).hasMessageThat().contains("It is too late to modify someStrings")
        assertThat(lockableSet).containsExactly("one")
    }


    @Test
    fun `check iterator locking removal`() {
        val lockableSet = LockableSet<String>("someStrings")
        lockableSet += setOf("one", "two")
        val iterator = lockableSet.iterator()
        assertThat(iterator.next()).isEqualTo("one")
        assertThat(iterator.hasNext()).named("iterator.hasNext()").isTrue()
        iterator.remove()
        assertThat(iterator.next()).isEqualTo("two")
        assertThat(lockableSet).containsExactly( "two")

        lockableSet.lock()

        val failure = assertFailsWith<AgpDslLockedException> {
            iterator.remove()
        }
        assertThat(failure).hasMessageThat().contains("It is too late to modify someStrings")
        assertThat(lockableSet).containsExactly( "two")
    }
}
