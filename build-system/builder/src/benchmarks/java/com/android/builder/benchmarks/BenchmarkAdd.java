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
package com.android.builder.benchmarks;

import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.Deflater;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BenchmarkAdd {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private void createFile(Path path, int size) throws IOException {
        Random random = new Random(1);
        byte[] content = new byte[size];
        random.nextBytes(content);
        FileOutputStream os = new FileOutputStream(path.toFile());
        os.write(content);
        os.close();
    }

    @Test
    public void run() throws IOException {
        System.out.println();
        System.out.println("Adding speed:");
        System.out.println("-------------");

        Path tmpFolder = temporaryFolder.newFolder().toPath();
        Path src = Paths.get(tmpFolder.toString(), "app.ap_");
        Path dst = Paths.get(tmpFolder.toString(), "aapt2_output.ap_");

        // Fake 32 MiB aapt2 like zip archive.
        ZipCreator.createZip(4000, 8500, src.toString());

        Map<String, Path> dexes = new HashMap<>();

        // Three fake dex files like what dex could produce.
        Path dex1 = Paths.get(tmpFolder.toString(), "classes18.dex");
        createFile(dex1, 54020);
        dexes.put("classes18.dex", dex1);

        Path dex2 = Paths.get(tmpFolder.toString(), "classes3.dex");
        createFile(dex2, 8683240);
        dexes.put("classes3.dex", dex2);

        Path dex3 = Paths.get(tmpFolder.toString(), "classes.dex");
        createFile(dex3, 132016);
        dexes.put("classes.dex", dex3);

        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        runZipFlinger(dst, dexes);

        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        runApkzlib(dst, dexes);
    }

    private void runZipFlinger(Path dst, Map<String, Path> dexes) throws IOException {
        StopWatch watch = new StopWatch();
        ZipArchive archive = new ZipArchive(dst.toFile());
        for (String name : dexes.keySet()) {
            Path path = dexes.get(name);
            BytesSource source = new BytesSource(path.toFile(), name, Deflater.NO_COMPRESSION);
            archive.add(source);
        }
        archive.close();

        long runtime = watch.end();
        System.out.println(String.format("Zipflinger:    %4d ms", runtime / 1_000_000));
    }

    private void runApkzlib(Path dst, Map<String, Path> dexes) throws IOException {
        StopWatch watch = new StopWatch();
        ZFile zFile = ZFile.openReadWrite(dst.toFile());
        for (String name : dexes.keySet()) {
            Path path = dexes.get(name);
            zFile.add(name, new FileInputStream(path.toFile()), false);
        }
        zFile.close();

        long runtime = watch.end();
        System.out.println(String.format("Apkzlib   :    %4d ms", runtime / 1_000_000));
    }
}
