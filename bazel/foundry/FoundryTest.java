import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
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
}
