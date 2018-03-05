import static org.junit.Assert.assertTrue;

import java.io.File;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
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
}
