/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.sdklib.AndroidVersion
import com.android.tools.lint.detector.api.JavaContext

/**
 * Utilities for handling library desugaring. This refers to a feature
 * implemented in Bazel to rewrite various library calls, such as
 * references to java.util.function, into calls to a backport library
 * which can run on older API levels.
 *
 * When projects are building with this support enabled, they don't want
 * the API detector to flag API calls for calls that are handled by
 * the library rewriter. This file contains calls which looks up whether
 * a given class/member reference is referencing an API that is rewritten.
 */
fun isApiDesugared(context: JavaContext, cls: String, name: String?): Boolean {
    // The specific set of APIs that are handled are described here:
    // https://github.com/bazelbuild/bazel/blob/master/tools/android/desugar.sh
    // The code below is based on the that file as of this revision:
    // https://github.com/bazelbuild/bazel/commit/2e677fb6b8f309b63558eb13294630a91ee0cd33
    // See also cs/desugar_java8_libs.sh, which adds:
    //  --rewrite_core_library_prefix java/util/concurrent/ConcurrentHashMap
    //  --rewrite_core_library_prefix java/util/concurrent/ThreadLocalRandom

    if (cls.startsWith("java.lang.")) {
        // --retarget_core_library_member "java/lang/Double#max->java/lang/Double8"
        // --retarget_core_library_member "java/lang/Double#min->java/lang/Double8"
        // --retarget_core_library_member "java/lang/Double#sum->java/lang/Double8"
        // --retarget_core_library_member "java/lang/Integer#max->java/lang/Integer8"
        // --retarget_core_library_member "java/lang/Integer#min->java/lang/Integer8"
        // --retarget_core_library_member "java/lang/Integer#sum->java/lang/Integer8"
        // --retarget_core_library_member "java/lang/Long#max->java/lang/Long8"
        // --retarget_core_library_member "java/lang/Long#min->java/lang/Long8"
        // --retarget_core_library_member "java/lang/Long#sum->java/lang/Long8"
        return when (cls) {
            "java.lang.Integer" -> name == "sum" || name == "min" || name == "max"
            "java.lang.Long" -> name == "sum" || name == "min" || name == "max"
            "java.lang.Double" -> name == "sum" || name == "min" || name == "max"
            "java.lang.Math" -> name == "toIntExact"
            "java.lang.annotation.Repeatable",
            "java.lang.annotation.ElementType" -> true
            else -> false
        }
    }

    if (cls.startsWith("java.util.")) {
        when (cls) {
            "java.util.Collection" -> {
                // All but parallelStream allowed, and parallel requires L
                if (name == "parallelStream") {
                    return context.mainProject.minSdk >= AndroidVersion.VersionCodes.LOLLIPOP
                }
                return true
            }
            "java.util.stream.BaseStream" -> {
                // All but parallel allowed, and it requires L
                if (name == "parallel") {
                    return context.mainProject.minSdk >= AndroidVersion.VersionCodes.LOLLIPOP
                }
                return true
            }
            "java.util.Arrays" -> {
                // --retarget_core_library_member "java/util/Arrays#stream->java/util/DesugarArrays"
                // --retarget_core_library_member "java/util/Arrays#spliterator->java/util/DesugarArrays"
                return name == "stream" || name == "spliterator"
            }
            "java.util.Calendar" -> {
                // --retarget_core_library_member "java/util/Calendar#toInstant->java/util/DesugarCalendar"
                return name == "toInstant"
            }
            "java.util.Date" -> {
                // --retarget_core_library_member "java/util/Date#from->java/util/DesugarDate"
                // --retarget_core_library_member "java/util/Date#toInstant->java/util/DesugarDate"
                return name == "from" || name == "toInstant"
            }
            "java.util.GregorianCalendar" -> {
                // --retarget_core_library_member "java/util/GregorianCalendar#from->java/util/DesugarGregorianCalendar"
                // --retarget_core_library_member "java/util/GregorianCalendar#toZonedDateTime->java/util/DesugarGregorianCalendar"
                return name == "from" || name == "toZonedDateTime"
            }
            "java.util.LinkedHashSet" -> {
                // --retarget_core_library_member "java/util/LinkedHashSet#spliterator->java/util/DesugarLinkedHashSet"
                return name == "spliterator"
            }
            "java.util.concurrent.atomic.AtomicInteger",
            "java.util.concurrent.atomic.AtomicLong",
            "java.util.concurrent.atomic.AtomicReference" -> {
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicInteger#getAndUpdate->java/util/concurrent/atomic/DesugarAtomicInteger"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicInteger#updateAndGet->java/util/concurrent/atomic/DesugarAtomicInteger"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicInteger#getAndAccumulate->java/util/concurrent/atomic/DesugarAtomicInteger"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicInteger#accumulateAndGet->java/util/concurrent/atomic/DesugarAtomicInteger"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicLong#getAndUpdate->java/util/concurrent/atomic/DesugarAtomicLong"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicLong#updateAndGet->java/util/concurrent/atomic/DesugarAtomicLong"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicLong#getAndAccumulate->java/util/concurrent/atomic/DesugarAtomicLong"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicLong#accumulateAndGet->java/util/concurrent/atomic/DesugarAtomicLong"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicReference#getAndUpdate->java/util/concurrent/atomic/DesugarAtomicReference"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicReference#updateAndGet->java/util/concurrent/atomic/DesugarAtomicReference"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicReference#getAndAccumulate->java/util/concurrent/atomic/DesugarAtomicReference"
                // --retarget_core_library_member "java/util/concurrent/atomic/AtomicReference#accumulateAndGet->java/util/concurrent/atomic/DesugarAtomicReference"
                return name == null || name == "getAndUpdate" ||
                        name == "updateAndGet" || name == "getAndAccumulate" ||
                        name == "accumulateAndGet"
            }

            // --rewrite_core_library_prefix java/util/DoubleSummaryStatistics
            // --rewrite_core_library_prefix java/util/IntSummaryStatistics
            // --rewrite_core_library_prefix java/util/LongSummaryStatistics
            // --rewrite_core_library_prefix java/util/Objects
            // --rewrite_core_library_prefix java/util/Optional
            // --rewrite_core_library_prefix java/util/PrimitiveIterator
            // --rewrite_core_library_prefix java/util/Spliterator
            // --rewrite_core_library_prefix java/util/StringJoiner
            "java.util.DoubleSummaryStatistics",
            "java.util.IntSummaryStatistics",
            "java.util.LongSummaryStatistics",
            "java.util.Objects",
            "java.util.Optional",
            "java.util.OptionalDouble", // matches java.util.Optional* which is a prefix
            "java.util.OptionalInt",
            "java.util.OptionalLong",
            "java.util.PrimitiveIterator",
            "java.util.Spliterator",
            "java.util.StringJoiner",

            // --emulate_core_library_interface java/util/Collection
            // --emulate_core_library_interface java/util/Map
            // --emulate_core_library_interface java/util/Map\$Entry
            // --emulate_core_library_interface java/util/Iterator
            // --emulate_core_library_interface java/util/Comparator
            // (java.util.Collection already handled above)
            "java.util.Map",
            "java.util.Map.Entry",
            "java.util.Iterator",
            "java.util.Comparator",

            // These are added for Blaze:
            //  --rewrite_core_library_prefix java/util/concurrent/ConcurrentHashMap
            //  --rewrite_core_library_prefix java/util/concurrent/ThreadLocalRandom
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ThreadLocalRandom" -> return true

            else -> {
                // --rewrite_core_library_prefix java/util/stream/
                // --rewrite_core_library_prefix java/util/function/
                if (cls.startsWith("java.util.stream") ||
                    cls.startsWith("java.util.function")
                ) {
                    return true
                }

                return false
            }
        }
    }

    //  --rewrite_core_library_prefix java/time/
    if (cls.startsWith("java.time.")) {
        return true
    }

    return false
}