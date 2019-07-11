/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.deploy.proto.Deploy.AgentSwapResponse;
import com.android.tools.deploy.proto.Deploy.JvmtiError;
import com.android.tools.deploy.proto.Deploy.SwapResponse;
import com.google.common.base.Enums;
import com.google.common.base.Optional;
import java.util.List;

public class InstallerResponseHandler {

    public void handle(SwapResponse response) throws DeployerException {
        if (response.getStatus() == SwapResponse.Status.OK) {
            return;
        }

        if (response.getStatus() != SwapResponse.Status.AGENT_ERROR) {
            throw DeployerException.swapFailed(response.getStatus());
        }

        handleAgentFailures(response.getFailedAgentsList());
    }

    private void handleAgentFailures(List<AgentSwapResponse> failedAgents)
            throws DeployerException {
        if (failedAgents.size() == 0) {
            return;
        }

        // For now, just pick the first failed agent; multiple agent errors only occurs in multi-process apps.
        AgentSwapResponse failedAgent = failedAgents.get(0);

        if (failedAgent.getStatus() == AgentSwapResponse.Status.CLASS_NOT_FOUND) {
            throw DeployerException.classNotFound(failedAgent.getClassName());
        }

        if (failedAgent.getStatus() == AgentSwapResponse.Status.JVMTI_ERROR) {
            handleJvmtiError(failedAgent.getJvmtiError());
        }

        throw DeployerException.agentSwapFailed(failedAgent.getStatus());
    }

    private void handleJvmtiError(JvmtiError jvmtiError) throws DeployerException {
        // If there are no detailed errors, report the JVMTI error code and return.
        if (jvmtiError.getDetailsCount() == 0) {
            Optional<JvmtiErrorCode> errorCode =
                    Enums.getIfPresent(JvmtiErrorCode.class, jvmtiError.getErrorCode());
            throw DeployerException.jvmtiError(errorCode.or(JvmtiErrorCode.UNKNOWN_JVMTI_ERROR));
        }

        // TODO: Currently, all detailed errors are add/remove resource related. Revisit.
        // TODO: How do we want to display the error if multiple resources were added/removed?
        JvmtiError.Details details = jvmtiError.getDetailsList().get(0);
        String parentClass = details.getClassName();
        String resType = parentClass.substring(parentClass.lastIndexOf('$') + 1);

        // Check for resource add before we check for resource remove, since the error
        // message for resource addition also covers resource renaming (one add + one remove).
        if (details.getType() == JvmtiError.Details.Type.FIELD_ADDED) {
            throw DeployerException.addedResources(details.getName(), resType);
        } else if (details.getType() == JvmtiError.Details.Type.FIELD_REMOVED) {
            throw DeployerException.removedResources(details.getName(), resType);
        } else {
            throw DeployerException.unknownJvmtiError(details.getType().name());
        }
    }
}
