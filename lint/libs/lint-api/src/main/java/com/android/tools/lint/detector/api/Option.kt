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

import com.android.tools.lint.client.api.Configuration
import com.android.utils.SdkUtils.wrap
import java.io.File

/**
 * Represents an option associated with a given [Issue] that can be
 * configured in a `lint.xml` file.
 */
sealed class Option(
    /** The name of the option, referenced in the `lint.xml` file */
    val name: String,

    /**
     * A brief (1 line) explanation of what this option represents,
     * such as "Whether to include deprecated members" or "Maximum
     * number of views to allow". Should be capitalized but not end
     * with punctuation like a period. The string should be in format
     * [TextFormat.RAW].
     */
    private val description: String,

    /**
     * Optional longer explanation of the option, formatted as
     * [TextFormat.RAW].
     */
    private val explanation: String? = null
) {
    /** Looks up the configured value for this option */
    abstract fun getValue(configuration: Configuration): Any?

    /** Looks up the configured value for the given [context] */
    abstract fun getValue(context: Context): Any?

    /** Returns the default value expressed as a string */
    abstract fun defaultAsString(): String?

    /** Returns a description of the allowed range, if applicable */
    open fun rangeAsString(): String? = null

    /** Returns the short description for this option */
    fun getDescription(format: TextFormat = TextFormat.RAW): String {
        val trimmed = description.trimIndent()
        return TextFormat.RAW.convertTo(trimmed, format)
    }

    /** Returns the (optional) longer explanation for this option */
    fun getExplanation(format: TextFormat = TextFormat.RAW): String? {
        explanation ?: return null
        val trimmed = explanation.trimIndent()
        // For convenience allow line wrapping in explanation raw strings
        // by "escaping" the newline, e.g. ending the line with \
        val message = trimmed.replace("\\\n", "")
        return TextFormat.RAW.convertTo(message, format)
    }

    /** Describes this option in a nice user format */
    fun describe(format: TextFormat = TextFormat.RAW, includeExample: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("**").append(name).append("**")
        val defaultValue = defaultAsString()
        if (defaultValue != null) {
            val esc = if (this is FileOption) "`" else ""
            sb.append(" (default is ").append(esc).append(defaultValue).append(esc).append(")")
        }
        sb.append(":")
        val desc = getDescription()
        val maxLineWidth = 72
        if (desc.length + sb.length > maxLineWidth) {
            sb.append("\n")
        } else {
            sb.append(" ")
        }
        sb.append(desc).append(".\n")
        val range = rangeAsString()
        if (range != null) {
            sb.append(range).append(".\n")
        }
        val explanation = getExplanation() ?: ""
        if (explanation.isNotBlank()) {
            sb.append("\n")
            if (format == TextFormat.TEXT) {
                sb.append(wrap(explanation, maxLineWidth, ""))
            } else {
                sb.append(explanation)
                sb.append("\n")
            }
        }
        if (includeExample) {
            sb.append("\n")
            sb.append("To configure this option, use a `lint.xml` file with an <option> like this:\n\n")
            sb.append(
                """
                ```xml
                <lint>
                    <issue id="${issue.id}">
                        <option name="$name" value="${defaultValue ?: "some string"}" />
                    </issue>
                </lint>
                ```
                """.trimIndent()
            )
        }
        return TextFormat.RAW.convertTo(sb.toString(), format)
    }

    override fun toString(): String = describe(TextFormat.TEXT)

    /**
     * Associated issue: automatically set when the option is registered
     * with an [Issue].
     */
    lateinit var issue: Issue
    protected fun ensureRegistered() {
        if (!this::issue.isInitialized) {
            error("Option $name has not been registered with an associated `Issue`; see `Issue.options()`")
        }
    }

    companion object {
        /** Describes a list of options */
        fun describe(options: List<Option>, format: TextFormat = TextFormat.RAW, includeExample: Boolean = true): String {
            if (options.isNotEmpty()) {
                val sb = StringBuilder()
                sb.append("Available options:\n")
                for (option in options) {
                    sb.append('\n').append(option.describe(TextFormat.RAW, includeExample)).append('\n')
                }
                return TextFormat.RAW.convertTo(sb.toString(), format)
            }

            return ""
        }
    }
}

/**
 * An [Option] of String type. For path strings, be sure to use
 * [FileOption].
 */
class StringOption(
    /** Option name. See [Option.name]. */
    name: String,

    /** Short option description. See [Option.description] */
    description: String,

    /** The default value, if any. */
    val defaultValue: String? = null,

    /** Longer explanation. See [Option.explanation] */
    explanation: String? = null
) : Option(name, description, explanation) {
    /**
     * Looks up the configured value for this option in the given
     * configuration.
     */
    override fun getValue(configuration: Configuration): String? {
        ensureRegistered()
        return configuration.getOption(this) as String? ?: defaultValue
    }

    override fun getValue(context: Context): String? =
        getValue(context.findConfiguration(context.file))

    override fun defaultAsString(): String? {
        return defaultValue?.let { "\"$it\"" }
    }
}

/** A Boolean [Option] */
class BooleanOption(
    /** Option name. See [Option.name]. */
    name: String,

    /** Short option description. See [Option.description] */
    description: String,

    /** The default value, if any. */
    val defaultValue: Boolean = false,

    /** Longer explanation. See [Option.explanation] */
    explanation: String? = null

) : Option(name, description, explanation) {
    override fun getValue(configuration: Configuration): Boolean {
        ensureRegistered()
        return configuration.getOption(this) as Boolean? ?: defaultValue
    }

    override fun getValue(context: Context): Boolean {
        val configuration = context.findConfiguration(context.file)
        return getValue(configuration)
    }

    override fun defaultAsString(): String {
        return defaultValue.toString()
    }
}

/** A integer [Option] */
class IntOption(
    /** Option name. See [Option.name]. */
    name: String,

    /** Short option description. See [Option.description] */
    description: String,

    /** The default value, if any. */
    val defaultValue: Int,

    /** Longer explanation. See [Option.explanation] */
    explanation: String? = null,

    /** Minimum allowed value, inclusive */
    val min: Int = Integer.MIN_VALUE,

    /** Maximum allowed value, exclusive */
    val max: Int = Integer.MAX_VALUE
) : Option(name, description, explanation) {
    override fun getValue(configuration: Configuration): Int {
        ensureRegistered()
        return configuration.getOption(this) as Int? ?: defaultValue
    }

    override fun getValue(context: Context): Int =
        getValue(context.findConfiguration(context.file))

    override fun rangeAsString(): String? {
        if (min == Int.MIN_VALUE) {
            if (max == Int.MAX_VALUE) {
                return null
            }
            return "Must be less than $max"
        } else if (max == Int.MAX_VALUE) {
            return "Must be at least $min"
        } else {
            return "Must be at least $min and less than $max"
        }
    }

    override fun defaultAsString(): String {
        return defaultValue.toString()
    }
}

/** A floating point [Option] */
class FloatOption(
    /** Option name. See [Option.name]. */
    name: String,

    /** Short option description. See [Option.description] */
    description: String,

    /** The default value, if any. */
    val defaultValue: Float,

    /** Longer explanation. See [Option.explanation] */
    explanation: String? = null,

    /** Minimum allowed value, inclusive */
    val min: Float = Float.MIN_VALUE,

    /** Maximum allowed value, exclusive */
    val max: Float = Float.MAX_VALUE
) : Option(name, description, explanation) {
    override fun getValue(configuration: Configuration): Float {
        ensureRegistered()
        return configuration.getOption(this) as Float? ?: defaultValue
    }

    override fun getValue(context: Context): Float =
        getValue(context.findConfiguration(context.file))

    override fun rangeAsString(): String? {
        if (min == Float.MIN_VALUE) {
            if (max == Float.MAX_VALUE) {
                return null
            }
            return "Must be less than $max"
        } else if (max == Float.MAX_VALUE) {
            return "Must be at least $min"
        } else {
            return "Must be at least $min and less than $max"
        }
    }

    override fun defaultAsString(): String {
        return defaultValue.toString()
    }
}

/** An [Option] representing a path */
class FileOption(
    /** Option name. See [Option.name]. */
    name: String,

    /** Short option description. See [Option.description] */
    description: String,

    /** The default value, if any. */
    val defaultValue: File? = null,

    /** Longer explanation. See [Option.explanation] */
    explanation: String? = null
) : Option(name, description, explanation) {
    override fun getValue(configuration: Configuration): File? {
        ensureRegistered()
        return configuration.getOption(this) as File? ?: defaultValue
    }

    override fun getValue(context: Context): File? =
        getValue(context.findConfiguration(context.file))

    override fun defaultAsString(): String? {
        return defaultValue?.path
    }
}
