@file:JvmName("Constraints")

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

package com.android.tools.lint.detector.api

fun minSdkAtLeast(minSdkVersion: Int): Constraint = MinSdkAtLeast(minSdkVersion)
fun minSdkLessThan(minSdkVersion: Int): Constraint = MinSdkLessThan(minSdkVersion)
fun targetSdkAtLeast(minSdkVersion: Int): Constraint = TargetSdkAtLeast(minSdkVersion)
fun targetSdkLessThan(minSdkVersion: Int): Constraint = TargetSdkLessThan(minSdkVersion)
fun isLibraryProject(): Constraint = IsLibraryProject()
fun isAndroidProject(): Constraint = IsAndroidProject()
fun notLibraryProject(): Constraint = NotLibraryProject()
fun notAndroidProject(): Constraint = NotAndroidProject()

/**
 * An optional condition to attach to an [Incident] to indicate that
 * whether the incident is valid depends on some conditions which cannot
 * be evaluated in the current project context.
 *
 * A very common condition is a minimumSdkVersion requirement. This
 * cannot be evaluated when for example a library is being analyzed,
 * because the actual minSdkVersion depends on the consuming app, which
 * is not available when the library is analyzed (and if the library is
 * consumed by more than one app the answers can be different in each
 * context).
 *
 * There are various built-in conditions for common operations.
 *
 * Note that this class is sealed such that only the specifically
 * enumerated conditions here are allowed. This is done because these
 * conditions must all be persisted, and to avoid having to introduce
 * a whole general serialization mechanism which can work with any
 * arbitrary class, instead we define a specific set of conditions, with
 * a general fallback mechanism.
 */
sealed class Constraint {
    /**
     * Returns true if the given incident should be reported. If the
     * answer is true, the condition implementation is allowed to mutate
     * state in the [Incident], such as updating the error message.
     */
    abstract fun accept(context: Context, incident: Incident): Boolean

    /**
     * Returns a condition where this condition *and* the given [other]
     * condition must be met.
     */
    infix fun and(other: Constraint): Constraint {
        return AllOfConstraint(this, other)
    }

    /**
     * Returns a condition where this condition *or* the given [other]
     * condition must be met.
     */
    infix fun or(other: Constraint): Constraint {
        return AnyOfConstraint(this, other)
    }
}

/**
 * Constraint checking that minSdkVersion >= x.
 *
 * Don't use this method directly; use [minSdkAtLeast] instead.
 */
class MinSdkAtLeast internal constructor(val minSdkVersion: Int) : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return context.mainProject.minSdk >= minSdkVersion
    }
}

/**
 * Constraint checking that minSdkVersion < x.
 *
 * Don't use this method directly; use [minSdkLessThan] instead.
 */
class MinSdkLessThan internal constructor(val minSdkVersion: Int) : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return context.mainProject.minSdk < minSdkVersion
    }
}

/**
 * Constraint checking that targetSdkVersion >= x.
 *
 * Don't use this method directly; use [targetSdkAtLeast] instead.
 */
class TargetSdkAtLeast internal constructor(val targetSdkVersion: Int) : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return context.mainProject.targetSdk >= targetSdkVersion
    }
}

/**
 * Constraint checking that targetSdkVersion < x.
 *
 * Don't use this method directly; use [targetSdkLessThan] instead.
 */
class TargetSdkLessThan internal constructor(val targetSdkVersion: Int) : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return context.mainProject.targetSdk < targetSdkVersion
    }
}

/**
 * Constraint checking that two constraints are both met.
 *
 * Don't use this method directly; use [Constraint.and] instead.
 */
class AllOfConstraint internal constructor(val left: Constraint, val right: Constraint) :
    Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return left.accept(context, incident) || right.accept(context, incident)
    }
}

/**
 * Constraint checking that either of two constraints are met.
 *
 * Don't use this method directly; use [Constraint.or] instead.
 */
class AnyOfConstraint internal constructor(val left: Constraint, val right: Constraint) :
    Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return left.accept(context, incident) && right.accept(context, incident)
    }
}

/**
 * Constraint checking that the main module is a library.
 *
 * Don't use this method directly; use [isLibraryProject] instead.
 */
@Suppress("CanSealedSubClassBeObject")
class IsLibraryProject internal constructor() : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return context.mainProject.isLibrary
    }
}

/**
 * Constraint checking that the main module is **not** a library.
 *
 * Don't use this method directly; use [notLibraryProject] instead.
 */
@Suppress("CanSealedSubClassBeObject")
class NotLibraryProject internal constructor() : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return !context.mainProject.isLibrary
    }
}

/**
 * Constraint checking that the main module is an Android project (app
 * or library).
 *
 * Don't use this method directly; use [isAndroidProject] instead.
 */
@Suppress("CanSealedSubClassBeObject")
class IsAndroidProject internal constructor() : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return context.mainProject.isAndroidProject
    }
}

/**
 * Constraint checking that the main module is **not** an Android
 * project (app or library).
 *
 * Don't use this method directly; use [notAndroidProject] instead.
 */
@Suppress("CanSealedSubClassBeObject")
class NotAndroidProject internal constructor() : Constraint() {
    override fun accept(context: Context, incident: Incident): Boolean {
        return !context.mainProject.isAndroidProject
    }
}
