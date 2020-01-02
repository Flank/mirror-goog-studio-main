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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.transport.TransportRule;
import com.android.tools.transport.device.SdkLevel;
import com.google.common.collect.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;

/** This basic test showcases some of the common ways to use the test transport framework. */
@RunWith(Parameterized.class)
public class SimpleTransportTest {
    @Parameters
    public static Collection<SdkLevel> parameters() {
        // Test both pre- and post-JVMTI devices.
        return Lists.newArrayList(SdkLevel.N, SdkLevel.O);
    }

    public static final String ACTIVITY_CLASS = "com.activity.SimpleActivity";
    @Rule
    public final TransportRule myTransportRule;

    public SimpleTransportTest(SdkLevel sdkLevel) {
        myTransportRule = new TransportRule(ACTIVITY_CLASS, sdkLevel);
    }

    @Test
    public void verifyBehaviorByCheckingDeviceLogs() {
        myTransportRule.getAndroidDriver().triggerMethod(ACTIVITY_CLASS, "printName");
        myTransportRule.getAndroidDriver().waitForInput("SimpleActivity");
    }

    @Test
    public void verifyBehaviorByUsingGrpcApis() {
        TransportServiceGrpc.TransportServiceBlockingStub transportStub =
                TransportServiceGrpc.newBlockingStub(myTransportRule.getGrpc().getChannel());

        Transport.VersionResponse version =
                transportStub.getVersion(Transport.VersionRequest.getDefaultInstance());
        assertThat(version.getVersion()).isNotEmpty();
    }
}
