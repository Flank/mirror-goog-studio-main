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

package com.android.build.gradle.internal.cxx

import com.android.build.gradle.internal.cxx.EditType.Insert
import com.android.build.gradle.internal.cxx.EditType.Delete
import com.android.build.gradle.internal.cxx.EditType.Replace
import com.google.common.annotations.VisibleForTesting
import com.google.protobuf.MessageOrBuilder
import com.google.protobuf.util.JsonFormat
import java.lang.Integer.min

/**
 * Give the shortest explanation for the difference between two multiline strings.
 * If there are no differences then return "".
 */
fun explainLineDifferences(before : String, after : String) : String {
    if (after == before) return ""
    val beforeLines = before.split("\n")
    val afterLines = after.split("\n")

    var difference = minimumEditDifference(beforeLines, afterLines)
    val sb = StringBuilder()
    while (difference != null) {
        val from = describeSpecial(beforeLines[difference.fromIndex])
        val to = describeSpecial(afterLines[difference.toIndex])

        when(difference.type) {
            Insert -> sb.append("INSERTED $to (at line ${difference.toIndex + 1})\n")
            Delete -> sb.append("DELETED $from (at line ${difference.fromIndex + 1})\n")
            Replace -> {
                val replacedPrefix = "REPLACED "
                val lineDifference = minimumEditDifference(from, to)!!
                val fromIndent = " ".repeat(replacedPrefix.length + lineDifference.fromIndex)
                val fromIndentShort = " ".repeat(lineDifference.fromIndex)
                when(lineDifference.type) {
                    Delete -> sb.append("$fromIndentShort[delete]-+\n$fromIndent|\n${fromIndent}v\n")
                    else -> { }
                }
                sb.append("$replacedPrefix$from (at line ${difference.fromIndex + 1})\n    with $to\n")
                val toIndent = " ".repeat(replacedPrefix.length + lineDifference.toIndex)
                val toIndentShort = " ".repeat(lineDifference.toIndex)
                when(lineDifference.type) {
                    Insert -> sb.append("$toIndent^\n$toIndent|\n$toIndentShort[insert]-+\n")
                    Replace -> sb.append("$toIndent^\n$toIndent|\n$toIndentShort[change]-+\n")
                    else -> { }
                }
            }
        }
        difference = difference.prior
    }
    return sb.toString().trim('\n')
}

private fun describeSpecial(value : String) = when(value) {
    "" -> "{NewLine}"
    else -> value
}

/**
 * Explain the difference between two strings on a char-by-char basis
 */
fun explainCharDifferences(before : String, after : String) : String {
    if (after == before) return ""

    var difference = minimumEditDifference(before, after)
    val sb = StringBuilder()
    var remaining = 6
    while (difference != null) {
        if (remaining-- == 0) {
            sb.append("and ${difference.count} more")
            break
        }
        val fromIndex = difference.fromIndex
        val from = before[fromIndex]
        val toIndex = difference.toIndex
        val to = after[difference.toIndex]
        when(difference.type) {
            Insert -> sb.append("INSERTED $to at $toIndex\n")
            Delete -> sb.append("DELETED $from at line $fromIndex\n")
            Replace -> sb.append("REPLACED '$from' at $fromIndex with '$to'\n")
        }
        difference = difference.prior
    }
    return sb.toString().trim('\n').apply { println(this) }
}

/**
 * Difference between two lists.
 */
private fun <Element> minimumEditDifference(
    from : List<Element>,
    to : List<Element>) = minimumEditDifferenceImpl(
        from = from.toIndexed(),
        to = to.toIndexed(),
        recorder = EditTrackingDifferenceRecorder()
    )

/**
 * Difference between two strings.
 */
private fun minimumEditDifference(
    from : CharSequence,
    to : CharSequence) = minimumEditDifferenceImpl(
        from = from.toIndexed(),
        to = to.toIndexed(),
        recorder = EditTrackingDifferenceRecorder()
    )

/**
 * Metric distance between two strings.
 */
@VisibleForTesting
fun minimumEditDistance(
    from : CharSequence,
    to : CharSequence) = minimumEditDifferenceImpl(
        from = from.toIndexed(),
        to = to.toIndexed(),
        recorder = CountingDifferenceRecorder()
    )

/**
 * Define the operations needed to evaluate differences between two collections
 */
private interface Indexed<Element> {
    val size : Int
    operator fun get(index : Int) : Element
}

private fun <T> List<T>.toIndexed() = object : Indexed<T> {
    override val size = this@toIndexed.size
    override fun get(index: Int) = this@toIndexed[index]
}

private fun CharSequence.toIndexed() = object : Indexed<Char> {
    override val size = this@toIndexed.length
    override fun get(index: Int) = this@toIndexed[index]
}

/**
 * Define functions needed to record differences and report them in [Record]
 */
private interface DifferenceRecorder<Record> {
    val default : Record
    fun edit(prior : Record, type : EditType, fromIndex : Int, toIndex : Int) : Record
    fun count(value : Record) : Int
    fun finalize(result : Record) : Record
}

/**
 * A [DifferenceRecorder] that just records the count of differences.
 */
private class CountingDifferenceRecorder : DifferenceRecorder<Int> {
    override val default = 0
    override fun edit(prior: Int, type: EditType, fromIndex: Int, toIndex: Int) = prior + 1
    override fun count(value: Int) = value
    override fun finalize(result: Int) = result
}

/**
 * A [DifferenceRecorder] that records the operations needed to transform from into to.
 */
private class EditTrackingDifferenceRecorder : DifferenceRecorder<Record?> {

    override val default: Record? = null

    override fun edit(prior: Record?, type: EditType, fromIndex: Int, toIndex: Int) =
        Record(
            prior = prior,
            count = (prior?.count ?: 0) + 1,
            type = type,
            fromIndex = fromIndex,
            toIndex = toIndex
        )

    override fun count(value: Record?) = value?.count ?: 0

    override fun finalize(result: Record?): Record? {
        // Reverse the list
        var current = result
        var previous: Record? = null
        var count = 1
        while (current != null) {
            val next = current.prior
            previous = current.copy(
                prior = previous,
                count = count++
            )
            current = next
        }
        return previous
    }
}

/**
 * The type of edit.
 */
private enum class EditType {
    Delete,
    Insert,
    Replace
}

/**
 * Description of an edit (insert, delete, or replace)
 */
private data class Record(
    val prior : Record?,
    val count : Int,
    val type : EditType,
    val fromIndex : Int,
    val toIndex : Int)

/**
 * Calculate the minimum edits (inserts, deletes, and replacements) needed to convert from
 * [from] to [to]. This is an implementation of Levenshtein distance
 * (https://en.wikipedia.org/wiki/Levenshtein_distance) modified to return the steps needed to
 * do the transformation rather than just the count of differences.
 */
private fun <Element, Record> minimumEditDifferenceImpl(
    from : Indexed<Element>,
    to : Indexed<Element>,
    recorder : DifferenceRecorder<Record>
) = with(recorder) {
    val m = from.size
    val n = to.size

    // Create and initialize the table of edits
    val edits = (0 .. m) .map { (0 .. n).map { default }.toMutableList() }
    repeat(m) { i -> edits[i + 1][0] = edit(edits[i][0], Delete, i, 0) }
    repeat(n) { j -> edits[0][j + 1] = edit(edits[0][j], Insert, 0, j) }

    // Progressively calculate the next set of edits leaving the final, minimal, edit distance in
    // the bottom-right element of the table (edits[m][n])
    repeat(m) { i ->
        repeat(n) { j ->
            val replace =
                if (to[j] == from[i]) edits[i][j]
                else edit(edits[i][j], Replace, i, j)
            val insert = edit(edits[i + 1][j], Insert, i, j)
            val delete = edit(edits[i][j + 1], Delete, i, j)

            edits[i + 1][j + 1] = when {
                count(replace) <= min(count(insert), count(delete)) -> replace
                count(insert) <= count(delete) -> insert
                else -> delete
            }
        }
    }
    finalize(edits[m][n])
}


