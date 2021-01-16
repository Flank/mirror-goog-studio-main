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

package com.android.tools.utp.plugins.host.icebox

import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto.Compression
import com.google.testing.platform.api.device.DeviceController
import io.grpc.ManagedChannelBuilder
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/*
 * IceboxCaller is an interface class to wait and query PID and call icebox in a new thread.
 */
interface IceboxCaller {
    public class IceboxException(message: String) : Exception(message)
    /**
     * Trigger icebox to monitor $testedApplicationID.
     */
    fun runIcebox(
            deviceController: DeviceController,
            testedApplicationID: String,
            snapshotNamePrefix: String,
            maxSnapshotNumber: Int,
            androidStudioDdmlibPort: Int
    )
    /**
     * Fetch a test failure snapshot.
     *
     * @param snapshotFile: the snapshot file to be exported into output artifacts
     * @param snapshotCompression: snapshot compression settings
     * @param emulatorSnapshotId: snapshot name known by the emulator
     */
    fun fetchSnapshot(
            snapshotFile: File,
            snapshotCompression: Compression,
            emulatorSnapshotId: String
    )
    /*
     * Cleanup and shutdown gRPC after everything is done.
     */
    fun shutdownGrpc()
}

/** Creates default instance of Icebox */
fun IceboxCaller(
        managedChannelBuilder: ManagedChannelBuilder<*>,
        grpcToken: String,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
): IceboxCaller = IceboxCallerImpl(managedChannelBuilder, grpcToken, coroutineScope)
