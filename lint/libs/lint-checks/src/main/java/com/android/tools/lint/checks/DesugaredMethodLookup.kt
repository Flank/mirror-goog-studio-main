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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.assertionsEnabled
import com.google.common.annotations.VisibleForTesting
import java.util.Arrays

/**
 * This class provides lookup for R8 method descriptors of its
 * backported methods.
 */
class DesugaredMethodLookup(private val methodDescriptors: Array<String>) {
    private fun isDesugared(owner: String, name: String, desc: String): Boolean {
        val signatureComparator: Comparator<String> = Comparator { o1, _ ->
            // We're not really comparing the two lambda parameters;
            // we're looking at the combination of o1 and the pair of
            // parameters passed in
            compare(owner, name, desc, o1)
        }
        return Arrays.binarySearch(methodDescriptors, "placeholder", signatureComparator) >= 0
    }

    private fun compare(owner: String, name: String, desc: String, combined: String): Int {
        var ownerIndex = 0
        var nameIndex = 0
        var descIndex = 0
        // We don't include the return type in description strings
        val lastParen = combined.lastIndexOf(')')
        val combinedLength = if (lastParen == -1) combined.length else lastParen + 1

        fun getNext(): Char {
            return when {
                ownerIndex < owner.length -> owner[ownerIndex++]
                nameIndex < name.length -> name[nameIndex++]
                descIndex < desc.length -> desc[descIndex++]
                else -> 0.toChar()
            }
        }

        var i = 0
        while (true) {
            val c = getNext()
            val d = combined[i++].let { if (it == '#') combined[i++] else it }
            if (c != d) {
                if (!c.isSymbolSeparator() || !d.isSymbolSeparator()) {
                    return d - c
                }
            }
            if (i == combinedLength) {
                // Allow matching just on class name, or method name, or on full descriptor
                return if (nameIndex == 0 || nameIndex == name.length && descIndex == 0 || descIndex == desc.length) 0 else 1
            }
        }
    }

    /**
     * Consider a.b.C.D to be equivalent to the internal name a/b/C$D.
     */
    private fun Char.isSymbolSeparator() = this == '/' || this == '$' || this == '.'

    companion object {
        /**
         * Checks whether the method for the given [owner], [name] and
         * internal [desc] string is desugared.
         */
        fun isDesugared(owner: String, name: String, desc: String): Boolean {
            return lookup.isDesugared(owner, name, desc)
        }

        /**
         * Sets the set of back-ported methods to be used for analysis
         * to the exact given list
         */
        fun setDesugaredMethods(fileContents: String) {
            val descriptors = fileContents.split('\n').toTypedArray()
            if (assertionsEnabled()) {
                // The file is required to be sorted (since it will be
                // used for binary search). Let's make sure it is. (This
                // is a file controlled by us (Android tools via R8 and
                // AGP) which is why we can make this a requirement
                // rather than explicitly sorting.)
                var prev = ""
                for (s in descriptors) {
                    assert(s.isEmpty() || s > prev)
                    prev = s
                }
            }
            lookup = DesugaredMethodLookup(descriptors)
        }

        /**
         * Returns the lookup to the default state. This is temporary;
         * once we switch to this being initialized from the lint model
         * there will be no static state here.
         */
        fun reset() {
            lookup = DesugaredMethodLookup(defaultDesugaredMethods)
        }

        @get:VisibleForTesting
        val defaultDesugaredMethods = arrayOf(
            // Created by
            // java -cp $ANDROID_HOME/cmdline-tools/latest/lib/r8.jar com.android.tools.r8.BackportedMethodList --min-api 15  \
            //     | awk '{ print "\"" $1 "\"," }' | sort
            "android/util/SparseArray#set(ILjava/lang/Object;)V",
            "java/lang/Boolean#compare(ZZ)I",
            "java/lang/Boolean#hashCode(Z)I",
            "java/lang/Boolean#logicalAnd(ZZ)Z",
            "java/lang/Boolean#logicalOr(ZZ)Z",
            "java/lang/Boolean#logicalXor(ZZ)Z",
            "java/lang/Byte#compare(BB)I",
            "java/lang/Byte#compareUnsigned(BB)I",
            "java/lang/Byte#hashCode(B)I",
            "java/lang/Byte#toUnsignedInt(B)I",
            "java/lang/Byte#toUnsignedLong(B)J",
            "java/lang/CharSequence#compare(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)I",
            "java/lang/Character#compare(CC)I",
            "java/lang/Character#hashCode(C)I",
            "java/lang/Character#toString(I)Ljava/lang/String;",
            "java/lang/Double#hashCode(D)I",
            "java/lang/Double#isFinite(D)Z",
            "java/lang/Double#max(DD)D",
            "java/lang/Double#min(DD)D",
            "java/lang/Double#sum(DD)D",
            "java/lang/Float#hashCode(F)I",
            "java/lang/Float#isFinite(F)Z",
            "java/lang/Float#max(FF)F",
            "java/lang/Float#min(FF)F",
            "java/lang/Float#sum(FF)F",
            "java/lang/Integer#compare(II)I",
            "java/lang/Integer#compareUnsigned(II)I",
            "java/lang/Integer#divideUnsigned(II)I",
            "java/lang/Integer#hashCode(I)I",
            "java/lang/Integer#max(II)I",
            "java/lang/Integer#min(II)I",
            "java/lang/Integer#parseInt(Ljava/lang/CharSequence;III)I",
            "java/lang/Integer#parseUnsignedInt(Ljava/lang/String;)I",
            "java/lang/Integer#parseUnsignedInt(Ljava/lang/String;I)I",
            "java/lang/Integer#remainderUnsigned(II)I",
            "java/lang/Integer#sum(II)I",
            "java/lang/Integer#toUnsignedLong(I)J",
            "java/lang/Integer#toUnsignedString(I)Ljava/lang/String;",
            "java/lang/Integer#toUnsignedString(II)Ljava/lang/String;",
            "java/lang/Long#compare(JJ)I",
            "java/lang/Long#compareUnsigned(JJ)I",
            "java/lang/Long#divideUnsigned(JJ)J",
            "java/lang/Long#hashCode(J)I",
            "java/lang/Long#max(JJ)J",
            "java/lang/Long#min(JJ)J",
            "java/lang/Long#parseLong(Ljava/lang/CharSequence;III)J",
            "java/lang/Long#parseUnsignedLong(Ljava/lang/CharSequence;III)J",
            "java/lang/Long#parseUnsignedLong(Ljava/lang/String;)J",
            "java/lang/Long#parseUnsignedLong(Ljava/lang/String;I)J",
            "java/lang/Long#remainderUnsigned(JJ)J",
            "java/lang/Long#sum(JJ)J",
            "java/lang/Long#toUnsignedString(J)Ljava/lang/String;",
            "java/lang/Long#toUnsignedString(JI)Ljava/lang/String;",
            "java/lang/Math#addExact(II)I",
            "java/lang/Math#addExact(JJ)J",
            "java/lang/Math#decrementExact(I)I",
            "java/lang/Math#decrementExact(J)J",
            "java/lang/Math#floorDiv(II)I",
            "java/lang/Math#floorDiv(JI)J",
            "java/lang/Math#floorDiv(JJ)J",
            "java/lang/Math#floorMod(II)I",
            "java/lang/Math#floorMod(JI)I",
            "java/lang/Math#floorMod(JJ)J",
            "java/lang/Math#incrementExact(I)I",
            "java/lang/Math#incrementExact(J)J",
            "java/lang/Math#multiplyExact(II)I",
            "java/lang/Math#multiplyExact(JI)J",
            "java/lang/Math#multiplyExact(JJ)J",
            "java/lang/Math#multiplyFull(II)J",
            "java/lang/Math#multiplyHigh(JJ)J",
            "java/lang/Math#negateExact(I)I",
            "java/lang/Math#negateExact(J)J",
            "java/lang/Math#nextDown(D)D",
            "java/lang/Math#nextDown(F)F",
            "java/lang/Math#subtractExact(II)I",
            "java/lang/Math#subtractExact(JJ)J",
            "java/lang/Math#toIntExact(J)I",
            "java/lang/Short#compare(SS)I",
            "java/lang/Short#compareUnsigned(SS)I",
            "java/lang/Short#hashCode(S)I",
            "java/lang/Short#toUnsignedInt(S)I",
            "java/lang/Short#toUnsignedLong(S)J",
            "java/lang/StrictMath#addExact(II)I",
            "java/lang/StrictMath#addExact(JJ)J",
            "java/lang/StrictMath#floorDiv(II)I",
            "java/lang/StrictMath#floorDiv(JI)J",
            "java/lang/StrictMath#floorDiv(JJ)J",
            "java/lang/StrictMath#floorMod(II)I",
            "java/lang/StrictMath#floorMod(JI)I",
            "java/lang/StrictMath#floorMod(JJ)J",
            "java/lang/StrictMath#multiplyExact(II)I",
            "java/lang/StrictMath#multiplyExact(JI)J",
            "java/lang/StrictMath#multiplyExact(JJ)J",
            "java/lang/StrictMath#multiplyFull(II)J",
            "java/lang/StrictMath#multiplyHigh(JJ)J",
            "java/lang/StrictMath#nextDown(D)D",
            "java/lang/StrictMath#nextDown(F)F",
            "java/lang/StrictMath#subtractExact(II)I",
            "java/lang/StrictMath#subtractExact(JJ)J",
            "java/lang/StrictMath#toIntExact(J)I",
            "java/lang/String#isBlank()Z",
            "java/lang/String#join(Ljava/lang/CharSequence;Ljava/lang/Iterable;)Ljava/lang/String;",
            "java/lang/String#join(Ljava/lang/CharSequence;[Ljava/lang/CharSequence;)Ljava/lang/String;",
            "java/lang/String#repeat(I)Ljava/lang/String;",
            "java/lang/String#strip()Ljava/lang/String;",
            "java/lang/String#stripLeading()Ljava/lang/String;",
            "java/lang/String#stripTrailing()Ljava/lang/String;",
            "java/util/Collections#emptyEnumeration()Ljava/util/Enumeration;",
            "java/util/Collections#emptyIterator()Ljava/util/Iterator;",
            "java/util/Collections#emptyListIterator()Ljava/util/ListIterator;",
            "java/util/List#copyOf(Ljava/util/Collection;)Ljava/util/List;",
            "java/util/List#of()Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            "java/util/List#of([Ljava/lang/Object;)Ljava/util/List;",
            "java/util/Map#copyOf(Ljava/util/Map;)Ljava/util/Map;",
            "java/util/Map#entry(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map\$Entry;",
            "java/util/Map#of()Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
            "java/util/Map#ofEntries([Ljava/util/Map\$Entry;)Ljava/util/Map;",
            "java/util/Objects#checkFromIndexSize(III)I",
            "java/util/Objects#checkFromToIndex(III)I",
            "java/util/Objects#checkIndex(II)I",
            "java/util/Objects#compare(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Comparator;)I",
            "java/util/Objects#deepEquals(Ljava/lang/Object;Ljava/lang/Object;)Z",
            "java/util/Objects#equals(Ljava/lang/Object;Ljava/lang/Object;)Z",
            "java/util/Objects#hash([Ljava/lang/Object;)I",
            "java/util/Objects#hashCode(Ljava/lang/Object;)I",
            "java/util/Objects#isNull(Ljava/lang/Object;)Z",
            "java/util/Objects#nonNull(Ljava/lang/Object;)Z",
            "java/util/Objects#requireNonNull(Ljava/lang/Object;)Ljava/lang/Object;",
            "java/util/Objects#requireNonNull(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
            "java/util/Objects#requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            "java/util/Objects#requireNonNullElseGet(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;",
            "java/util/Objects#toString(Ljava/lang/Object;)Ljava/lang/String;",
            "java/util/Objects#toString(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;",
            "java/util/Set#copyOf(Ljava/util/Collection;)Ljava/util/Set;",
            "java/util/Set#of()Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
            "java/util/Set#of([Ljava/lang/Object;)Ljava/util/Set;"
        )

        /**
         * Temporarily mutable such that we can set this from a command
         * line flag instead of initializing it via the lint model
         * (while we're still working out how this is best passed -- as
         * strings, files, shipped as resource files in r8 that the lint
         * model points to, etc.)
         */
        var lookup: DesugaredMethodLookup = DesugaredMethodLookup(defaultDesugaredMethods)
    }
}
