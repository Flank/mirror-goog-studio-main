/*
 * Copyright (C) 2020 The Android Open Source Project
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

import java.util.EnumSet;

public class DeployerOption {
    public final boolean useOptimisticSwap;
    public final boolean useOptimisticResourceSwap;
    public final EnumSet<ChangeType> optimisticInstallSupport;
    public final boolean useStructuralRedefinition;
    public final boolean useVariableReinitialization;
    public final boolean fastRestartOnSwapFail;
    public final boolean enableCoroutineDebugger;

    private DeployerOption(
            boolean useOptimisticSwap,
            boolean useOptimisticResourceSwap,
            EnumSet<ChangeType> optimisticInstallSupport,
            boolean useStructuralRedefinition,
            boolean useVariableReinitialization,
            boolean fastRestartOnSwapFail,
            boolean enableCoroutineDebugger) {
        this.useOptimisticSwap = useOptimisticSwap;
        this.useOptimisticResourceSwap = useOptimisticResourceSwap;
        this.optimisticInstallSupport = optimisticInstallSupport;
        this.useStructuralRedefinition = useStructuralRedefinition;
        this.useVariableReinitialization = useVariableReinitialization;
        this.fastRestartOnSwapFail = fastRestartOnSwapFail;
        this.enableCoroutineDebugger = enableCoroutineDebugger;
    }

    public static class Builder {
        private boolean useOptimisticSwap;
        private boolean useOptimisticResourceSwap;
        private EnumSet<ChangeType> optimisticInstallSupport = EnumSet.noneOf(ChangeType.class);
        private boolean useStructuralRedefinition;
        private boolean useVariableReinitialization;
        private boolean fastRestartOnSwapFail;
        private boolean enableCoroutineDebugger;

        public Builder setUseOptimisticSwap(boolean useOptimisticSwap) {
            this.useOptimisticSwap = useOptimisticSwap;
            return this;
        }

        public Builder setUseOptimisticResourceSwap(boolean useOptimisticResourceSwap) {
            this.useOptimisticResourceSwap = useOptimisticResourceSwap;
            return this;
        }

        public Builder setOptimisticInstallSupport(EnumSet<ChangeType> supportedChanges) {
            this.optimisticInstallSupport = supportedChanges;
            return this;
        }

        public Builder setUseStructuralRedefinition(boolean useStructuralRedefinition) {
            this.useStructuralRedefinition = useStructuralRedefinition;
            return this;
        }

        public Builder setUseVariableReinitialization(boolean useVariableReinitialization) {
            this.useVariableReinitialization = useVariableReinitialization;
            return this;
        }

        public Builder setFastRestartOnSwapFail(boolean fastRestartOnSwapFail) {
            this.fastRestartOnSwapFail = fastRestartOnSwapFail;
            return this;
        }

        public Builder enableCoroutineDebugger(boolean enableCoroutineDebugger) {
            this.enableCoroutineDebugger = enableCoroutineDebugger;
            return this;
        }

        public DeployerOption build() {
            return new DeployerOption(
                    useOptimisticSwap,
                    useOptimisticResourceSwap,
                    optimisticInstallSupport,
                    useStructuralRedefinition,
                    useVariableReinitialization,
                    fastRestartOnSwapFail,
                    enableCoroutineDebugger);
        }
    }
}
