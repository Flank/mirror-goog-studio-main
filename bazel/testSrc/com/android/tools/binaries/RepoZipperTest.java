package com.android.tools.binaries;

import static com.google.common.truth.Truth.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RepoZipperTest {

    private static final String POM_TEMPLATE =
            "<project>"
                    + "  <modelVersion>4.0.0</modelVersion>"
                    + "  <groupId>%s</groupId>"
                    + "  <artifactId>%s</artifactId>"
                    + "  <version>%s</version>"
                    + "</project>";

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File writeStubPom(String name, String groupId, String artifactId, String version)
            throws IOException {
        File file = temp.newFile(name);
        String pom = String.format(POM_TEMPLATE, groupId, artifactId, version);
        Files.write(file.toPath(), pom.getBytes());
        return file;
    }

    private String readZipEntry(ZipFile zip, ZipEntry entry) throws IOException {
        InputStream zis = zip.getInputStream(entry);
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
    }

    @Test
    public void buildZip() throws Exception {
        // Build stub POMs, artifacts, and manifest.
        File pom1 = writeStubPom("1.pom", "g1", "a1", "1.0");
        File pom2 = writeStubPom("2.pom", "g2", "a2", "2.0");
        File artifact1 = temp.newFile("1.jar");
        File artifact2 = temp.newFile("2.jar");
        Files.write(artifact1.toPath(), "1.jar".getBytes());
        Files.write(artifact2.toPath(), "2.jar".getBytes());

        File manifest = temp.newFile("repo.manifest");
        Files.write(
                manifest.toPath(),
                (pom1.toString()
                                + "\n"
                                + artifact1.toString()
                                + "\n"
                                + pom2.toString()
                                + "\n"
                                + artifact2.toString())
                        .getBytes());

        // Run the zipper.
        File output = temp.newFile("out.zip");
        RepoZipper.buildZip(output, manifest.toPath());

        // Verify the contents of the output zip.
        try (ZipFile zip = new ZipFile(output)) {
            assertThat(zip.size()).isEqualTo(4);

            ZipEntry pomEntry1 = zip.getEntry("g1/a1/1.0/a1-1.0.pom");
            ZipEntry pomEntry2 = zip.getEntry("g2/a2/2.0/a2-2.0.pom");
            ZipEntry artifactEntry1 = zip.getEntry("g1/a1/1.0/a1-1.0.jar");
            ZipEntry artifactEntry2 = zip.getEntry("g2/a2/2.0/a2-2.0.jar");

            assertThat(pomEntry1).isNotNull();
            assertThat(pomEntry2).isNotNull();
            assertThat(artifactEntry1).isNotNull();
            assertThat(artifactEntry2).isNotNull();

            String contents1 = readZipEntry(zip, artifactEntry1);
            String contents2 = readZipEntry(zip, artifactEntry2);

            assertThat(contents1).isEqualTo("1.jar");
            assertThat(contents2).isEqualTo("2.jar");
        }
    }
}
