/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.detector.api.TextFormat.RAW
import com.google.common.annotations.Beta
import java.util.ArrayList

/**
 * An issue is a potential bug in an Android application. An issue is discovered
 * by a [Detector], and has an associated [Severity].
 *
 * Issues and detectors are separate classes because a detector can discover
 * multiple different issues as it's analyzing code, and we want to be able to
 * different severities for different issues, the ability to suppress one but
 * not other issues from the same detector, and so on.
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
class Issue private constructor(
    /**
     * Returns the unique id of this issue. These should not change over time
     * since they are used to persist the names of issues suppressed by the user
     * etc. It is typically a single camel-cased word.
     *
     * @return the associated fixed id, never null and always unique
     */
    val id: String,

    private val briefDescription: String,

    private val explanation: String,

    /**
     * The primary category of the issue
     *
     * @return the primary category of the issue, never null
     */
    val category: Category,

    /**
     * Returns a priority, in the range 1-10, with 10 being the most severe and
     * 1 the least
     *
     * @return a priority from 1 to 10
     */
    val priority: Int,

    /**
     * Returns the default severity of the issues found by this detector (some
     * tools may allow the user to specify custom severities for detectors).
     *
     *
     * Note that even though the normal way for an issue to be disabled is for
     * the [Configuration] to return [Severity.IGNORE], there is a
     * [.isEnabledByDefault] method which can be used to turn off issues
     * by default. This is done rather than just having the severity as the only
     * attribute on the issue such that an issue can be configured with an
     * appropriate severity (such as [Severity.ERROR]) even when issues
     * are disabled by default for example because they are experimental or not
     * yet stable.
     *
     * @return the severity of the issues found by this detector
     */
    val defaultSeverity: Severity,

    /**
     * The implementation for the given issue. This is typically done by
     * IDEs that can offer a replacement for a given issue which performs better
     * or in some other way works better within the IDE.
     */
    var implementation: Implementation
) : Comparable<Issue> {
    private var moreInfoUrls: Any? = null
    private var enabledByDefault = true

    /**
     * A link (a URL string) to more information, or null
     */
    val moreInfo: List<String>
        get() {
            when (moreInfoUrls) {
                null -> return emptyList()
                is String -> return listOf(moreInfoUrls as String)
                else -> {
                    assert(moreInfoUrls is List<*>)
                    @Suppress("UNCHECKED_CAST")
                    return moreInfoUrls as List<String>
                }
            }
        }

    init {
        assert(!briefDescription.isEmpty())
        assert(!explanation.isEmpty())
    }

    /**
     * Briefly (in a couple of words) describes these errors
     *
     * @return a brief summary of the issue, never null, never empty
     */
    fun getBriefDescription(format: TextFormat): String {
        return RAW.convertTo(briefDescription.trim { it <= ' ' }, format)
    }

    /**
     * Describes the error found by this rule, e.g.
     * "Buttons must define contentDescriptions". Preferably the explanation
     * should also contain a description of how the problem should be solved.
     * Additional info can be provided via [.getMoreInfo].
     *
     * @param format the format to write the format as
     * @return an explanation of the issue, never null, never empty
     */
    fun getExplanation(format: TextFormat): String {
        return RAW.convertTo(explanation.trim { it <= ' ' }, format)
    }

    /**
     * Adds a more info URL string
     *
     * @param moreInfoUrl url string
     * @return this, for constructor chaining
     */
    fun addMoreInfo(moreInfoUrl: String): Issue {
        // Nearly all issues supply at most a single URL, so don't bother with
        // lists wrappers for most of these issues
        when (moreInfoUrls) {
            null -> moreInfoUrls = moreInfoUrl
            is String -> {
                val existing = moreInfoUrls as String
                val list = ArrayList<String>(2)
                list.add(existing)
                list.add(moreInfoUrl)
                moreInfoUrls = list
            }
            else -> {
                assert(moreInfoUrls is List<*>)
                @Suppress("UNCHECKED_CAST")
                (moreInfoUrls as MutableList<String>).add(moreInfoUrl)
            }
        }
        return this
    }

    /**
     * Returns whether this issue should be enabled by default, unless the user
     * has explicitly disabled it.
     *
     * @return true if this issue should be enabled by default
     */
    fun isEnabledByDefault(): Boolean {
        return enabledByDefault
    }

    /**
     * Sorts the detectors alphabetically by id. This is intended to make it
     * convenient to store settings for detectors in a fixed order. It is not
     * intended as the order to be shown to the user; for that, a tool embedding
     * lint might consider the priorities, categories, severities etc of the
     * various detectors.
     *
     * @param other the [Issue] to compare this issue to
     */
    override fun compareTo(other: Issue): Int {
        return id.compareTo(other.id)
    }

    /**
     * Sets whether this issue is enabled by default.
     *
     * @param enabledByDefault whether the issue should be enabled by default
     * @return this, for constructor chaining
     */
    fun setEnabledByDefault(enabledByDefault: Boolean): Issue {
        this.enabledByDefault = enabledByDefault
        return this
    }

    override fun toString(): String {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val issue = other as Issue?

        return id == issue!!.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        /**
         * Creates a new issue. The description strings can use some simple markup;
         * see the [TextFormat.RAW] documentation
         * for details.
         *
         * @param id the fixed id of the issue
         * @param briefDescription short summary (typically 5-6 words or less), typically
         * describing the **problem** rather than the **fix**
         * (e.g. "Missing minSdkVersion")
         * @param explanation a full explanation of the issue, with suggestions for
         * how to fix it
         * @param category the associated category, if any
         * @param priority the priority, a number from 1 to 10 with 10 being most
         * important/severe
         * @param severity the default severity of the issue
         * @param implementation the default implementation for this issue
         * @return a new [Issue]
         */
        @JvmStatic
        fun create(
            id: String,
            briefDescription: String,
            explanation: String,
            category: Category,
            priority: Int,
            severity: Severity,
            implementation: Implementation
        ): Issue {
            return Issue(
                id, briefDescription, explanation, category, priority,
                severity, implementation
            )
        }

        /**
         * Creates a new issue. The description strings can use some simple markup;
         * see the [TextFormat.RAW] documentation
         * for details.
         *
         * @param id the fixed id of the issue
         * @param briefDescription short summary (typically 5-6 words or less), typically
         * describing the **problem** rather than the **fix**
         * (e.g. "Missing minSdkVersion")
         * @param explanation a full explanation of the issue, with suggestions for
         * @param moreInfo additional information URL
         * how to fix it
         * @param category the associated category, if any
         * @param priority the priority, a number from 1 to 10 with 10 being most
         * important/severe
         * @param severity the default severity of the issue
         * @param implementation the default implementation for this issue
         * @return a new [Issue]
         */
        fun create(
            id: String,
            briefDescription: String,
            explanation: String,
            moreInfo: String? = null,
            category: Category = Category.CORRECTNESS,
            priority: Int = 5,
            severity: Severity = Severity.WARNING,
            implementation: Implementation,
            enabledByDefault: Boolean = true
        ): Issue {
            val issue = Issue(
                id, briefDescription, explanation, category, priority,
                severity, implementation
            )
            if (moreInfo != null) {
                issue.addMoreInfo(moreInfo)
            }
            if (!enabledByDefault) {
                issue.setEnabledByDefault(false)
            }

            return issue
        }
    }
}
