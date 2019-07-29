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

package signflinger;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.util.RunnablesExecutor;
import com.android.apksig.util.RunnablesProvider;
import com.android.testutils.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;

public class Utils {
    private static final String BASE = "tools/base/signflinger/tests/resources/";

    // getWorkspaceFile will fail if not running within "bazel test" or Intellij test runner.
    // This is the case for "bazel run" (benchmarks).
    static File getFile(String path) {
        String fullPath = BASE + path;
        File prospect = new File(fullPath);
        if (prospect.exists()) {
            return prospect;
        }
        return TestUtils.getWorkspaceFile(fullPath);
    }

    static Path getPath(String path) {
        return getFile(path).toPath();
    }

    static File getTestOutputFile(String path) throws IOException {
        String directories = TestUtils.getTestOutputDir().getAbsolutePath() + File.separator + BASE;
        Files.createDirectories(Paths.get(directories));
        return new File(directories + path);
    }

    static Path getTestOutputPath(String path) throws IOException {
        return getTestOutputFile(path).toPath();
    }

    private static long fileId = 0;

    static void createZip(long numFiles, int sizePerFile, File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }

        Random random = new Random(1);
        try (FileOutputStream f = new FileOutputStream(file);
                ZipOutputStream s = new ZipOutputStream(f)) {
            for (int i = 0; i < numFiles; i++) {
                long id = fileId++;
                String name = String.format("file%06d", id);
                ZipEntry entry = new ZipEntry(name);
                byte[] bytes = new byte[sizePerFile];
                random.nextBytes(bytes);
                s.putNextEntry(entry);
                s.write(bytes);
                s.closeEntry();
            }
            ZipEntry entry = new ZipEntry("AndroidManifest.xml");
            s.putNextEntry(entry);
            byte[] bytes = Files.readAllBytes(Utils.getPath("AndroidManifest.xml"));
            s.write(bytes);
            s.closeEntry();
        }
    }

    static RunnablesExecutor createExecutor() {
        RunnablesExecutor executor =
                (RunnablesProvider provider) -> {
                    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
                    int jobCount = forkJoinPool.getParallelism();
                    List<Future<?>> jobs = new ArrayList<>(jobCount);

                    for (int i = 0; i < jobCount; i++) {
                        jobs.add(forkJoinPool.submit(provider.createRunnable()));
                    }

                    try {
                        for (Future<?> future : jobs) {
                            future.get();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                };
        return executor;
    }

    static void verify(File file) throws ApkFormatException, NoSuchAlgorithmException, IOException {
        ApkVerifier.Builder apkVerifierBuilder = new ApkVerifier.Builder(file);
        apkVerifierBuilder.setMinCheckedPlatformVersion(24);
        ApkVerifier verifier = apkVerifierBuilder.build();
        ApkVerifier.Result result = verifier.verify();
        if (result.containsErrors()) {
            System.out.println(result.getErrors());
            System.out.println(result.getWarnings());
        }
        Assert.assertTrue(result.isVerified());
    }

    static void copy(Path src, Path dst) throws IOException {
        if (Files.exists(dst)) {
            Files.delete(dst);
        }
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    public static PrivateKey toPrivateKey(String resourceName, String keyAlgorithm)
            throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(getPath(resourceName));

        // Keep overly strictly linter happy by limiting what JCA KeyFactory algorithms are used
        // here
        KeyFactory keyFactory;
        switch (keyAlgorithm.toUpperCase(Locale.US)) {
            case "RSA":
                keyFactory = KeyFactory.getInstance("rsa");
                break;
            case "DSA":
                keyFactory = KeyFactory.getInstance("dsa");
                break;
            case "EC":
                keyFactory = KeyFactory.getInstance("ec");
                break;
            default:
                throw new InvalidKeySpecException("Unsupported key algorithm: " + keyAlgorithm);
        }

        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    public static List<X509Certificate> toCertificateChain(String resourceName)
            throws IOException, CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        byte[] bytes = Files.readAllBytes(getPath(resourceName));
        Collection<? extends Certificate> certs =
                certificateFactory.generateCertificates(new ByteArrayInputStream(bytes));
        List<X509Certificate> result = new ArrayList<>(certs.size());
        for (Certificate cert : certs) {
            result.add((X509Certificate) cert);
        }
        return result;
    }

    protected static SignerConfig getSignerConfig(String algoName, String subName)
            throws Exception {
        PrivateKey privateKey = toPrivateKey(algoName + "-" + subName + ".pk8", algoName);
        List<X509Certificate> certs = toCertificateChain(algoName + "-" + subName + ".x509.pem");
        ApkSigner.SignerConfig signerConfig =
                new ApkSigner.SignerConfig.Builder(algoName, privateKey, certs).build();
        SignerConfig signer =
                new SignerConfig(signerConfig.getPrivateKey(), signerConfig.getCertificates());
        return signer;
    }
}
