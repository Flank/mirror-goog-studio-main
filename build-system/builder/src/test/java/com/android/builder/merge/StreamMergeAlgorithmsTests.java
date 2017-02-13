/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.merge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Function;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class StreamMergeAlgorithmsTests {

    /**
     * Receives the output of merging.
     */
    @NonNull
    private ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();

    /**
     * Transforms a two dimensional array of data into a list of input streams, each returning an
     * element from the array.
     *
     * @param data the input arrays; must contain at least one element
     * @return a list with as many elements as {@code data}
     */
    @NonNull
    private ImmutableList<InputStream> makeInputs(@NonNull byte[][] data) {
        Preconditions.checkArgument(data.length > 0);

        ImmutableList.Builder<InputStream> builder = ImmutableList.builder();
        for (byte[] streamData : data) {
            builder.add(new ByteArrayInputStream(streamData));
        }

        return builder.build();
    }

    @Test
    public void pickFirstOneFile() {
        StreamMergeAlgorithm pickFirst = StreamMergeAlgorithms.pickFirst();
        pickFirst.merge("foo", makeInputs(new byte [][] { { 1, 2, 3 } }), bytesOut);
        assertArrayEquals(new byte[] { 1, 2, 3 }, bytesOut.toByteArray());
    }

    @Test
    public void pickFirstTwoFiles() {
        StreamMergeAlgorithm pickFirst = StreamMergeAlgorithms.pickFirst();
        pickFirst.merge("foo", makeInputs(new byte [][] { { 1, 2, 3 }, { 4, 5, 6 } }), bytesOut);
        assertArrayEquals(new byte[] { 1, 2, 3 }, bytesOut.toByteArray());
    }

    @Test
    public void concatOneFile() {
        StreamMergeAlgorithm concat = StreamMergeAlgorithms.concat();
        concat.merge("foo", makeInputs(new byte [][] { { 1, 2, 3 } }), bytesOut);
        assertArrayEquals(new byte[] { 1, 2, 3, '\n' }, bytesOut.toByteArray());
    }

    @Test
    public void concatTwoFiles() {
        StreamMergeAlgorithm concat = StreamMergeAlgorithms.concat();
        concat.merge("foo", makeInputs(new byte [][] { { 1, 2, 3 }, { 4, 5, 6 } }), bytesOut);
        assertArrayEquals(new byte[] { 1, 2, 3, '\n', 4, 5, 6, '\n' }, bytesOut.toByteArray());
    }

    @Test
    public void acceptOnlyOneOneFile() {
        StreamMergeAlgorithm acceptOne = StreamMergeAlgorithms.acceptOnlyOne();
        acceptOne.merge("foo", makeInputs(new byte[][] { { 1, 2, 3 } }), bytesOut);
        assertArrayEquals(new byte[] { 1, 2, 3 }, bytesOut.toByteArray());
    }

    @Test
    public void acceptOnlyOneTwoFiles() {
        StreamMergeAlgorithm acceptOne = StreamMergeAlgorithms.acceptOnlyOne();
        try {
            acceptOne.merge("foo", makeInputs(new byte[][]{ { 1, 2, 3 }, { 4, 5, 6 } }), bytesOut);
            fail();
        } catch (DuplicateRelativeFileException e) {
            /*
             * Expected.
             */
        }
    }

    @Test
    public void select() {
        StreamMergeAlgorithm alg1 = Mockito.mock(StreamMergeAlgorithm.class);
        StreamMergeAlgorithm alg2 = Mockito.mock(StreamMergeAlgorithm.class);
        Function<String, StreamMergeAlgorithm> f = (p -> p.equals("foo")? alg1 : alg2);

        StreamMergeAlgorithm select = StreamMergeAlgorithms.select(f);

        ImmutableList<InputStream> inputs = makeInputs(new byte[][] { { 1, 2, 3 } });
        select.merge("foo", inputs, bytesOut);

        Mockito.verify(alg1).merge(
                Matchers.eq("foo"),
                Matchers.same(inputs),
                Matchers.same(bytesOut));
        Mockito.verifyZeroInteractions(alg2);

        select.merge("bar", inputs, bytesOut);
        Mockito.verifyZeroInteractions(alg1);
        Mockito.verify(alg2).merge(
                Matchers.eq("bar"),
                Matchers.same(inputs),
                Matchers.same(bytesOut));

    }
}
