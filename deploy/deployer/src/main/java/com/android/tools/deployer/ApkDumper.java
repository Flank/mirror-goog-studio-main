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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApkDumper {

    private final Installer installer;

    public ApkDumper(Installer installer) {
        this.installer = installer;
    }

    public List<ApkEntry> dump(String packageName) throws DeployerException {
        try {
            Deploy.DumpResponse response = installer.dump(packageName);
            if (response.getStatus() == Deploy.DumpResponse.Status.ERROR_PACKAGE_NOT_FOUND) {
                throw new DeployerException(
                        DeployerException.Error.DUMP_UNKNOWN_PACKAGE,
                        "Cannot list apks for package " + packageName + ". Is the app installed?");
            }

            return convert(response.getDumpsList());
        } catch (IOException e) {
            throw new DeployerException(DeployerException.Error.DUMP_FAILED, e);
        }
    }

    private List<ApkEntry> convert(List<Deploy.ApkDump> protoDumps) {
        List<ApkEntry> dumps = new ArrayList<>();
        for (Deploy.ApkDump dump : protoDumps) {
            ByteBuffer cd = dump.getCd().asReadOnlyByteBuffer();
            ByteBuffer signature = dump.getSignature().asReadOnlyByteBuffer();
            HashMap<String, ZipUtils.ZipEntry> zipEntries = ZipUtils.readZipEntries(cd);
            cd.rewind();
            String digest = ZipUtils.digest(signature.remaining() != 0 ? signature : cd);
            Apk apk =
                    Apk.builder()
                            .setName(dump.getName())
                            .setChecksum(digest)
                            .setPath(dump.getAbsolutePath())
                            .setZipEntries(zipEntries)
                            .build();
            for (Map.Entry<String, ZipUtils.ZipEntry> entry : zipEntries.entrySet()) {
                dumps.add(new ApkEntry(entry.getKey(), entry.getValue().crc, apk));
            }
        }
        return dumps;
    }
}
