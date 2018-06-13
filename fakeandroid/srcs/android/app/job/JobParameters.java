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

import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;

public class JobParameters {
    private final int jobId;
    private final PersistableBundle extras;
    private final Bundle transientExtras;
    private final boolean overrideDeadlineExpired;
    private Uri[] triggeredContentUris;
    private String[] triggeredContentAuthorities;

    public JobParameters(
            int jobId,
            PersistableBundle extras,
            Bundle transientExtras,
            boolean overrideDeadlineExpired,
            Uri[] triggeredContentUris,
            String[] triggerContentAuthorities) {
        this.jobId = jobId;
        this.extras = extras;
        this.transientExtras = transientExtras;
        this.overrideDeadlineExpired = overrideDeadlineExpired;
        this.triggeredContentAuthorities = triggerContentAuthorities;
        this.triggeredContentUris = triggeredContentUris;
    }

    public int getJobId() {
        return jobId;
    }

    public PersistableBundle getExtras() {
        return extras;
    }

    public Bundle getTransientExtras() {
        return transientExtras;
    }

    public boolean isOverrideDeadlineExpired() {
        return overrideDeadlineExpired;
    }

    public Uri[] getTriggeredContentUris() {
        return triggeredContentUris;
    }

    public String[] getTriggeredContentAuthorities() {
        return triggeredContentAuthorities;
    }
}
