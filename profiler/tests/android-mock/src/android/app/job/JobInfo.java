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

package android.app.job;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.List;

public class JobInfo {
    private int mJobId;
    private ComponentName mService;
    private int mBackoffPolicy;
    private long mInitialBackoffMs;
    private boolean mIsPeriodic;
    private long mFlexMs;
    private List<TriggerContentUri> mTriggerContentUris;
    private long mIntervalMs;
    private long mMinLatencyMs;
    private long mMaxExecutionDelayMs;
    private int mNetworkType;
    private long mTriggerContentMaxDelay;
    private long mTriggerContentUpdateDelay;
    private boolean mIsPersisted;
    private boolean mIsRequireBatteryNotLow;
    private boolean mIsRequireCharging;
    private boolean mIsRequireDeviceIdle;
    private boolean mIsRequireStorageNotLow;
    private PersistableBundle mExtras;
    private Bundle mTransientExtras;

    private JobInfo() {}

    public int getId() {
        return mJobId;
    }

    public ComponentName getService() {
        return mService;
    }

    public int getBackoffPolicy() {
        return mBackoffPolicy;
    }

    public long getInitialBackoffMillis() {
        return mInitialBackoffMs;
    }

    public boolean isPeriodic() {
        return mIsPeriodic;
    }

    public long getFlexMillis() {
        return mFlexMs;
    }

    public long getIntervalMillis() {
        return mIntervalMs;
    }

    public long getMinLatencyMillis() {
        return mMinLatencyMs;
    }

    public long getMaxExecutionDelayMillis() {
        return mMaxExecutionDelayMs;
    }

    public int getNetworkType() {
        return mNetworkType;
    }

    public TriggerContentUri[] getTriggerContentUris() {
        if (mTriggerContentUris == null) {
            return null;
        }
        return mTriggerContentUris.toArray(new TriggerContentUri[mTriggerContentUris.size()]);
    }

    public long getTriggerContentMaxDelay() {
        return mTriggerContentMaxDelay;
    }

    public long getTriggerContentUpdateDelay() {
        return mTriggerContentUpdateDelay;
    }

    public boolean isPersisted() {
        return mIsPersisted;
    }

    public boolean isRequireBatteryNotLow() {
        return mIsRequireBatteryNotLow;
    }

    public boolean isRequireCharging() {
        return mIsRequireCharging;
    }

    public boolean isRequireDeviceIdle() {
        return mIsRequireDeviceIdle;
    }

    public boolean isRequireStorageNotLow() {
        return mIsRequireStorageNotLow;
    }

    public PersistableBundle getExtras() {
        return mExtras;
    }

    public Bundle getTransientExtras() {
        return mTransientExtras;
    }

    public static class Builder {
        private JobInfo jobInfo;

        public Builder(int jobId, ComponentName service) {
            jobInfo = new JobInfo();
            jobInfo.mJobId = jobId;
            jobInfo.mService = service;
            jobInfo.mExtras = new PersistableBundle();
            jobInfo.mTransientExtras = new Bundle();
        }

        public JobInfo build() {
            return jobInfo;
        }

        public Builder setBackoffCriteria(long initialBackoffMillis, int backoffPolicy) {
            jobInfo.mInitialBackoffMs = initialBackoffMillis;
            jobInfo.mBackoffPolicy = backoffPolicy;
            return this;
        }

        public Builder setPeriodic(long intervalMillis, long flexMillis) {
            jobInfo.mIsPeriodic = true;
            jobInfo.mIntervalMs = intervalMillis;
            jobInfo.mFlexMs = flexMillis;
            return this;
        }

        public Builder setMinimumLatency(long minLatencyMillis) {
            jobInfo.mMinLatencyMs = minLatencyMillis;
            return this;
        }

        public Builder setOverrideDeadline(long maxExecutionDelayMillis) {
            jobInfo.mMaxExecutionDelayMs = maxExecutionDelayMillis;
            return this;
        }

        public Builder setRequiresNetworkType(int networkType) {
            jobInfo.mNetworkType = networkType;
            return this;
        }

        public Builder addTriggerContentUri(TriggerContentUri uri) {
            if (jobInfo.mTriggerContentUris == null) {
                jobInfo.mTriggerContentUris = new ArrayList<TriggerContentUri>();
            }
            jobInfo.mTriggerContentUris.add(uri);
            return this;
        }

        public Builder setTriggerContentMaxDelay(long durationMs) {
            jobInfo.mTriggerContentMaxDelay = durationMs;
            return this;
        }

        public Builder setTriggerContentUpdateDelay(long durationMs) {
            jobInfo.mTriggerContentUpdateDelay = durationMs;
            return this;
        }

        public Builder setPersisted(boolean isPersistend) {
            jobInfo.mIsPersisted = isPersistend;
            return this;
        }

        public Builder setRequiresBatteryNotLow(boolean batteryNotLow) {
            jobInfo.mIsRequireBatteryNotLow = batteryNotLow;
            return this;
        }

        public Builder setRequiresCharging(boolean requiresCharging) {
            jobInfo.mIsRequireCharging = requiresCharging;
            return this;
        }

        public Builder setRequiresDeviceIdle(boolean requiresDeviceIdle) {
            jobInfo.mIsRequireDeviceIdle = requiresDeviceIdle;
            return this;
        }

        public Builder setRequiresStorageNotLow(boolean storageNotLow) {
            jobInfo.mIsRequireStorageNotLow = storageNotLow;
            return this;
        }

        public Builder setExtras(PersistableBundle extras) {
            jobInfo.mExtras = extras;
            return this;
        }

        public Builder setTransientExtras(Bundle extras) {
            jobInfo.mTransientExtras = extras;
            return this;
        }
    }

    public static final class TriggerContentUri {
        private final Uri mUri;

        public TriggerContentUri(Uri uri) {
            mUri = uri;
        }

        public Uri getUri() {
            return mUri;
        }
    }
}
