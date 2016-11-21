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

package com.android.apkzlib.zip;

import com.android.apkzlib.zip.compress.DeflateExecutionCompressor;
import com.android.apkzlib.zip.utils.ByteTracker;
import java.util.zip.Deflater;
import javax.annotation.Nonnull;

/**
 * Options to create a {@link ZFile}.
 */
public class ZFileOptions {

    /**
     * The byte tracker.
     */
    @Nonnull
    private ByteTracker mTracker;

    /**
     * The compressor to use.
     */
    @Nonnull
    private Compressor mCompressor;

    /**
     * Should timestamps be zeroed?
     */
    private boolean mNoTimestamps;

    /**
     * The alignment rule to use.
     */
    @Nonnull
    private AlignmentRule mAlignmentRule;

    /**
     * Should the extra field be used to cover empty space?
     */
    private boolean mCoverEmptySpaceUsingExtraField;

    /**
     * Should files be automatically sorted before update?
     */
    private boolean mAutoSortFiles;

    /**
     * Should validation of the data descriptors of entries be skipped? See
     * {@link #getSkipDataDescriptorValidation()}
     */
    private boolean mSkipDataDescriptionValidation;

    /**
     * Creates a new options object. All options are set to their defaults.
     */
    public ZFileOptions() {
        mTracker = new ByteTracker();
        mCompressor =
                new DeflateExecutionCompressor(
                        Runnable::run,
                        mTracker,
                        Deflater.DEFAULT_COMPRESSION);
        mAlignmentRule = AlignmentRules.compose();
    }

    /**
     * Obtains the ZFile's byte tracker.
     *
     * @return the byte tracker
     */
    @Nonnull
    public ByteTracker getTracker() {
        return mTracker;
    }

    /**
     * Obtains the compressor to use.
     *
     * @return the compressor
     */
    @Nonnull
    public Compressor getCompressor() {
        return mCompressor;
    }

    /**
     * Sets the compressor to use.
     *
     * @param compressor the compressor
     */
    public void setCompressor(@Nonnull Compressor compressor) {
        mCompressor = compressor;
    }

    /**
     * Obtains whether timestamps should be zeroed.
     *
     * @return should timestamps be zeroed?
     */
    public boolean getNoTimestamps() {
        return mNoTimestamps;
    }

    /**
     * Sets whether timestamps should be zeroed.
     *
     * @param noTimestamps should timestamps be zeroed?
     */
    public void setNoTimestamps(boolean noTimestamps) {
        mNoTimestamps = noTimestamps;
    }

    /**
     * Obtains the alignment rule.
     *
     * @return the alignment rule
     */
    @Nonnull
    public AlignmentRule getAlignmentRule() {
        return mAlignmentRule;
    }

    /**
     * Sets the alignment rule.
     *
     * @param alignmentRule the alignment rule
     */
    public void setAlignmentRule(@Nonnull AlignmentRule alignmentRule) {
        mAlignmentRule = alignmentRule;
    }

    /**
     * Obtains whether the extra field should be used to cover empty spaces. See {@link ZFile} for
     * an explanation on using the extra field for covering empty spaces.
     *
     * @return should the extra field be used to cover empty spaces?
     */
    public boolean getCoverEmptySpaceUsingExtraField() {
        return mCoverEmptySpaceUsingExtraField;
    }

    /**
     * Sets whether the extra field should be used to cover empty spaces. See {@link ZFile} for an
     * explanation on using the extra field for covering empty spaces.
     *
     * @param coverEmptySpaceUsingExtraField should the extra field be used to cover empty spaces?
     */
    public void setCoverEmptySpaceUsingExtraField(boolean coverEmptySpaceUsingExtraField) {
        mCoverEmptySpaceUsingExtraField = coverEmptySpaceUsingExtraField;
    }

    /**
     * Obtains whether files should be automatically sorted before updating the zip file. See
     * {@link ZFile} for an explanation on automatic sorting.
     *
     * @return should the file be automatically sorted?
     */
    public boolean getAutoSortFiles() {
        return mAutoSortFiles;
    }

    /**
     * Sets whether files should be automatically sorted before updating the zip file. See
     * {@link ZFile} for an explanation on automatic sorting.
     *
     * @param autoSortFiles should the file be automatically sorted?
     */
    public void setAutoSortFiles(boolean autoSortFiles) {
        mAutoSortFiles = autoSortFiles;
    }

    /**
     * Should data descriptor validation be skipped? This should generally be
     * set to false. However, some tools (proguard -- http://b.android.com/221057) generate zips
     * with incorrect data descriptors and to open the zips we need to skip the validation of data
     * descriptors.
     *
     * @return should data descriptors be validated?
     */
    public boolean getSkipDataDescriptorValidation() {
        return mSkipDataDescriptionValidation;
    }

    /**
     * Sets whether data descriptors validation should be skipped. See
     * {@link #getSkipDataDescriptorValidation()}.
     *
     * @param skip should validation be skipped?
     */
    public void setSkipDataDescriptionValidation(boolean skip) {
        mSkipDataDescriptionValidation = skip;
    }
}
