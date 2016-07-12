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

package com.android.builder.internal.packaging.zip;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.packaging.zip.utils.LittleEndianUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Contains an extra field.
 *
 * <p>According to the zip specification, the extra field is composed of a sequence of fields.
 * This class provides a way to access, parse and modify that information.
 *
 * <p>The zip specification calls fields to the fields inside the extra field. Because this
 * terminology is confusing, we use <i>segment</i> to refer to a part of the extra field. Each
 * segment is represented by an instance of {@link Segment} and contains a header ID and data.
 *
 * <p>Each instance of {@link ExtraField} is immutable. The extra field of a particular entry can
 * be changed by creating a new instanceof {@link ExtraField} and pass it to
 * {@link StoredEntry#setLocalExtra(ExtraField)}.
 *
 * <p>Instances of {@link ExtraField} can be created directly from the list of segments in it
 * or from the raw byte data. If created from the raw byte data, the data will only be parsed
 * on demand. So, if neither {@link #getSegments()} nor {@link #getSingleSegment(int)} is
 * invoked, the extra field will not be parsed. This guarantees low performance impact of the
 * using the extra field unless its contents are needed.
 */
public class ExtraField {

    /**
     * Header ID for field with zip alignment.
     */
    static final int ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID = 0xd935;

    /**
     * The field's raw data, if it is known. Either this variable or {@link #mSegments} must be
     * non-{@code null}.
     */
    @Nullable
    private final byte[] mRawData;

    /**
     * The list of field's segments. Will be populated if the extra field is created based on a
     * list of segments; will also be populated after parsing if the extra field is created based
     * on the raw bytes.
     */
    @Nullable
    private ImmutableList<Segment> mSegments;

    /**
     * Creates an extra field based on existing raw data.
     *
     * @param rawData the raw data; will not be parsed unless needed
     */
    public ExtraField(@NonNull byte[] rawData) {
        mRawData = rawData;
        mSegments = null;
    }

    /**
     * Creates a new extra field with no segments.
     */
    public ExtraField() {
        mRawData = null;
        mSegments = ImmutableList.of();
    }

    /**
     * Creates a new extra field with the given segments.
     *
     * @param segments the segments
     */
    public ExtraField(@NonNull ImmutableList<Segment> segments) {
        mRawData = null;
        mSegments = segments;
    }

    /**
     * Obtains all segments in the extra field.
     *
     * @return all segments
     * @throws IOException failed to parse the extra field
     */
    public ImmutableList<Segment> getSegments() throws IOException {
        if (mSegments == null) {
            parseSegments();
        }

        Preconditions.checkNotNull(mSegments);
        return mSegments;
    }

    /**
     * Obtains the only segment with the provided header ID.
     *
     * @param headerId the header ID
     * @return the segment found or {@code null} if no segment contains the provided header ID
     * @throws IOException there is more than one header with the provided header ID
     */
    @Nullable
    public Segment getSingleSegment(int headerId) throws IOException {
        List<Segment> found =
                getSegments().stream()
                        .filter(s -> s.getHeaderId() == headerId)
                        .collect(Collectors.toList());
        if (found.isEmpty()) {
            return null;
        } else if (found.size() == 1) {
            return found.get(0);
        } else {
            throw new IOException(found.size() + " segments with header ID " + headerId + "found");
        }
    }

    /**
     * Parses the raw data and generates all segments in {@link #mSegments}.
     *
     * @throws IOException failed to parse the data
     */
    private void parseSegments() throws IOException {
        Preconditions.checkNotNull(mRawData);
        Preconditions.checkState(mSegments == null);

        List<Segment> segments = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(mRawData);

        while (buffer.remaining() > 0) {
            int headerId = LittleEndianUtils.readUnsigned2Le(buffer);
            int dataSize = LittleEndianUtils.readUnsigned2Le(buffer);
            if (dataSize < 0) {
                throw new IOException(
                        "Invalid data size for extra field segment with header ID "
                                + headerId
                                + ": "
                                + dataSize);
            }

            byte[] data = new byte[dataSize];
            buffer.get(data);

            SegmentFactory factory = identifySegmentFactory(headerId);
            Segment seg = factory.make(headerId, data);
            segments.add(seg);
        }

        mSegments = ImmutableList.copyOf(segments);
    }

    /**
     * Obtains the size of the extra field.
     *
     * @return the size
     */
    public int size() {
        if (mRawData != null) {
            return mRawData.length;
        } else {
            Preconditions.checkNotNull(mSegments);
            int sz = 0;
            for (Segment s : mSegments) {
                sz += s.size();
            }

            return sz;
        }
    }

    /**
     * Writes the extra field to the given output buffer.
     *
     * @param out the output buffer to write the field; exactly {@link #size()} bytes will be
     * written
     * @throws IOException failed to write the extra fields
     */
    public void write(@NonNull ByteBuffer out) throws IOException {
        if (mRawData != null) {
            out.put(mRawData);
        } else {
            Preconditions.checkNotNull(mSegments);
            for (Segment s : mSegments) {
                s.write(out);
            }
        }
    }

    /**
     * Identifies the factory to create the segment with the provided header ID.
     *
     * @param headerId the header ID
     * @return the segmnet factory that creates segments with the given header
     */
    @NonNull
    private static SegmentFactory identifySegmentFactory(int headerId) {
        if (headerId == ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID) {
            return AlignmentSegment::new;
        }

        return RawDataSegment::new;
    }

    /**
     * Field inside the extra field. A segment contains a header ID and data. Specific types of
     * segments implement this interface.
     */
    public interface Segment {

        /**
         * Obtains the segment's header ID.
         *
         * @return the segment's header ID
         */
        int getHeaderId();

        /**
         * Obtains the size of the segment including the header ID.
         *
         * @return the number of bytes needed to write the segment
         */
        int size();

        /**
         * Writes the segment to a buffer.
         *
         * @param out the buffer where to write the segment to; exactly {@link #size()} bytes will
         * be written
         * @throws IOException failed to write segment data
         */
        void write(@NonNull ByteBuffer out) throws IOException;
    }

    /**
     * Factory that creates a segment.
     */
    @FunctionalInterface
    interface SegmentFactory {

        /**
         * Creates a new segment.
         *
         * @param headerId the header ID
         * @param data the segment's data
         * @return the created segment
         * @throws IOException failed to create the segment from the data
         */
        @NonNull
        Segment make(int headerId, @NonNull byte[] data) throws IOException;
    }

    /**
     * Segment of raw data: this class represents a general segment containing an array of bytes
     * as data.
     */
    public static class RawDataSegment implements Segment {

        /**
         * Header ID.
         */
        private final int mHeaderId;

        /**
         * Data in the segment.
         */
        @NonNull
        private final byte[] mData;

        /**
         * Creates a new raw data segment.
         *
         * @param headerId the header ID
         * @param data the segment data
         */
        RawDataSegment(int headerId, @NonNull byte[] data) {
            mHeaderId = headerId;
            mData = data;
        }

        @Override
        public int getHeaderId() {
            return mHeaderId;
        }

        @Override
        public void write(@NonNull ByteBuffer out) throws IOException {
            LittleEndianUtils.writeUnsigned2Le(out, mHeaderId);
            LittleEndianUtils.writeUnsigned2Le(out, mData.length);
            out.put(mData);
        }

        @Override
        public int size() {
            return 4 + mData.length;
        }
    }

    /**
     * Segment with information on an alignment: this segment contains information on how an entry
     * should be aligned and contains zero-filled data to force alignment.
     *
     * <p>An alignment segment contains the header ID, the size of the data, the alignment value
     * and zero bytes to pad
     */
    public static class AlignmentSegment implements Segment {

        /**
         * The alignment value.
         */
        private int mAlignment;

        /**
         * How many bytes of padding are in this segment?
         */
        private int mPadding;

        /**
         * Creates a new alignment segment.
         *
         * @param alignment the alignment value
         * @param totalSize how many bytes should this segment take?
         */
        public AlignmentSegment(int alignment, int totalSize) {
            Preconditions.checkArgument(alignment > 0, "alignment <= 0");
            Preconditions.checkArgument(totalSize >= 6, "totalSize < 6");

            /*
             * We have 6 bytes of fixed data: header ID (2 bytes), data size (2 bytes), alignment
             * value (2 bytes).
             */
            mAlignment = alignment;
            mPadding = totalSize - 6;
        }

        /**
         * Creates a new alignment segment from extra data.
         *
         * @param headerId the header ID
         * @param data the segment data
         * @throws IOException failed to create the segment from the data
         */
        public AlignmentSegment(int headerId, @NonNull byte[] data) throws IOException {
            Preconditions.checkArgument(headerId == ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID);

            ByteBuffer dataBuffer = ByteBuffer.wrap(data);
            mAlignment = LittleEndianUtils.readUnsigned2Le(dataBuffer);
            if (mAlignment <= 0) {
                throw new IOException("Invalid alignment in alignment field: " + mAlignment);
            }

            mPadding = data.length - 2;
        }

        @Override
        public void write(@NonNull ByteBuffer out) throws IOException {
            LittleEndianUtils.writeUnsigned2Le(out, ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID);
            LittleEndianUtils.writeUnsigned2Le(out, mPadding + 2);
            LittleEndianUtils.writeUnsigned2Le(out, mAlignment);
            out.put(new byte[mPadding]);
        }

        @Override
        public int size() {
            return mPadding + 6;
        }

        @Override
        public int getHeaderId() {
            return ALIGNMENT_ZIP_EXTRA_DATA_FIELD_HEADER_ID;
        }
    }
}
