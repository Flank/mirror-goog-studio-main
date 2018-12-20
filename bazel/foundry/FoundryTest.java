import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FoundryTest {

    @Test
    public void testAapt2() throws Exception {
        File testLocalDir = new File(".");
        String osName = System.getProperty("os.name").toLowerCase();
        String[] env = {
            "LD_PRELOAD="
                    + testLocalDir.getAbsolutePath()
                    + "/prebuilts/tools/common/aapt/linux/lib64/libc++.so"
        };
        if (osName.equalsIgnoreCase("Linux")) {
            execute("prebuilts/tools/common/aapt/" + osName + "/aapt2 -h", env, 1);
        } else {
            execute(
                    "prebuilts/tools/common/aapt/"
                            + osName
                            + "/aapt2 -h".replace('/', File.separatorChar),
                    null,
                    1);
        }
    }

    @Test
    public void testCA() throws Exception {
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                X509Certificate[] issurers = ((X509TrustManager) manager).getAcceptedIssuers();
                for (X509Certificate c : issurers) {
                    System.out.println(c);
                }
                if (issurers.length != 0) {
                    return;
                }
            }
        }
        throw new AssertionError("No issurers found");
    }

    @Test
    public void testRandom() throws Exception {
        File rnd = new File("/dev/random");
        assertTrue(rnd.isDirectory());
        // Running this with -Djava.security.debug=all shows in the logs that it fails to use /dev/random
        byte[] seed = SecureRandom.getInstance("SHA1PRNG").generateSeed(24);
    }

    @Test
    public void testCreateFile() {
        File tempDir = new File("/tmp");
        String filename = "你所有的基地都属于我们.txt";
        System.out.println("locale info: " + Locale.getDefault());
        System.out.println("defaultCharset: " + Charset.defaultCharset());
        try {
            File file = new File(tempDir, filename);
            file.createNewFile();
            System.out.println("File created.");
            System.out.println("tempDir.list(): " + Arrays.toString(tempDir.list()));
            assertTrue(Arrays.asList(tempDir.list()).contains(filename));
        } catch (IOException e) {
            fail("File with non-ascii name failed to be created.");
        }
    }

    @Test
    public void testArmLinuxLinker() {
        execute("prebuilts/studio/sdk/linux/build-tools/28.0.3/arm-linux-androideabi-ld --help");
    }

    @Test
    public void testJdkVersion() throws IOException {
        String javaVersionInUse = System.getProperty("java.version");
        // Get the java version from our prebuilts jdk.
        ProcessBuilder builder =
                new ProcessBuilder("prebuilts/studio/jdk/linux/bin/java", "-version");
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String firstLine = r.readLine(); // e.g. openjdk version "1.8.0_152-release"
        int lastQuote = firstLine.lastIndexOf("\"");
        int openQuote = firstLine.substring(0, lastQuote).lastIndexOf("\"");
        String prebuiltsJavaVersion = firstLine.substring(openQuote + 1, lastQuote);
        assertEquals(javaVersionInUse, prebuiltsJavaVersion);
    }

    private void execute(String command) {
        execute(command, null, 0);
    }

    private void execute(String command, String[] env, int expectedExitValue) {
        try {
            Process proc =
                    env != null
                            ? Runtime.getRuntime().exec(command, env)
                            : Runtime.getRuntime().exec(command);

            BufferedReader input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader error = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            int exitVal = proc.waitFor();
            String line;
            while ((line = input.readLine()) != null) {
                System.out.println(line);
            }
            System.out.println("Error Stream");
            while ((line = error.readLine()) != null) {
                System.out.println(line);
            }
            input.close();
            error.close();
            System.out.println("Process exitValue: " + exitVal);
            assertTrue(exitVal == expectedExitValue);
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Could not run " + command);
        }
    }
}
