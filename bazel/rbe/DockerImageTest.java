import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Validates conditions expected to be present in the RBE docker image.
 *
 * <p> This test may assert conditions that are present in the docker image,
 * but not on the host machine. Currently this is not the case, and the
 * docker image is only validated when this test is run on RBE.
 * If this were to change, this test should still pass locally by checking
 * image-specific state such as the environment variable 'STUDIO_IMAGE'.
 */
@RunWith(JUnit4.class)
public final class DockerImageTest {

    /** Tests for the presence of required 32bit libraries, such as lib32z1. */
    @Test
    public void testAapt2() throws Exception {
        File testLocalDir = new File(".");
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.equalsIgnoreCase("Linux")) {
            execute("prebuilts/tools/common/aapt/" + osName + "/aapt2 -h", null, 1);
        } else {
            execute(
                    "prebuilts/tools/common/aapt/"
                            + osName
                            + "/aapt2 -h".replace('/', File.separatorChar),
                    null,
                    1);
        }
    }

    /** Tests CA certs are available from java, required by IJ integration tests. */
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
        // TODO(navabi): Write testRandom() to test urandom on our rbe docker container.
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
