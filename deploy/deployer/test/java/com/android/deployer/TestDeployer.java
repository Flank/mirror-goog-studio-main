package com.android.tools.deployer;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.HashMap;
import org.junit.Test;

public class TestDeployer {

    private static final String BASE = "tools/base/deploy/deployer/test/resource/";

    @Test
    public void testCentralDirectoryParse() throws Exception {
        File file = getFile("base.apk.remotecd");
        ZipCentralDirectory zcd = new ZipCentralDirectory(file);
        HashMap<String, Long> crcs = new HashMap<>();
        zcd.getCrcs(crcs);
        assertEquals(crcs.size(), 1007);

        long manifestCrc = crcs.get("AndroidManifest.xml");
        assertEquals(manifestCrc, 0x5804A053);
    }

    private File getFile(String filename) {
        File file = new File(BASE, filename);
        return file;
    }
}
