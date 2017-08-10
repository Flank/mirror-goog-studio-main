package com.android.tools.profiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import org.junit.Assert;
import org.junit.rules.ExternalResource;

/**
 * This class writes out a properties file that is used by perfd to determine device settings. The
 * file written indicates the SDK and release of the phone being simulated by the test.
 */
public class DeviceProperties extends ExternalResource {

    private String myCodeName;
    private String myRelease;
    private String mySdk;

    public DeviceProperties(String codeName, String release, String sdk) {
        myCodeName = codeName;
        myRelease = release;
        mySdk = sdk;
    }

    public void writeFile() {
        try {
            File propertiesFile = new File("device_info.prop");
            // We always expect to create a new file.
            PrintStream stream = new PrintStream(propertiesFile);
            stream.println("ro.build.version.codename=" + myCodeName);
            stream.println("ro.build.version.release=" + myRelease);
            stream.println("ro.build.version.sdk=" + mySdk);
        } catch (IOException ex) {
            Assert.fail("Failed to write prop file: " + ex);
        }
    }
}
