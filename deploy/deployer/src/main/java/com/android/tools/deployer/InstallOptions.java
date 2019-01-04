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
package com.android.tools.deployer;

import java.util.ArrayList;
import java.util.List;

public final class InstallOptions {

    private final List<String> flags;

    private InstallOptions(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getFlags() {
        return flags;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> flags;

        private Builder() {
            this.flags = new ArrayList<>();
        }

        // Allows test packages to be installed.
        public Builder setAllowDebuggable() {
            flags.add("-t");
            return this;
        }

        // Grants all runtime permissions listed in the application manifest to the application upon install.
        public Builder setGrantAllPermissions() {
            flags.add("-g");
            return this;
        }

        // Installs application as non-ephemeral full app.
        public Builder setInstallFullApk() {
            flags.add("--full");
            return this;
        }

        // Sets a string of user-specified installation flags to be passed to the installer.
        public Builder setUserInstallOptions(String userSpecifiedFlags) {
            if (userSpecifiedFlags != null && !userSpecifiedFlags.isEmpty()) {
                flags.add(userSpecifiedFlags);
            }
            return this;
        }

        public InstallOptions build() {
            return new InstallOptions(flags);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstallOptions that = (InstallOptions) o;
        return flags.equals(that.flags);
    }

    @Override
    public int hashCode() {
        return flags.hashCode();
    }
}
