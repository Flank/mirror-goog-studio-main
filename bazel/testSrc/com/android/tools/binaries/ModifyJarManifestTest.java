package com.android.tools.binaries;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.file.Files.readAllBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(JUnit4.class)
public class ModifyJarManifestTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File jarFile;
    private File output;

    @Before
    public void setupFiles() throws IOException {
        jarFile = tempFolder.newFile("foo.jar");
        output = new File(tempFolder.getRoot(), "foo-modified.jar");
    }

    @Test
    public void modifyJar_noManifest_doesNotModify() throws Exception {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream((jarFile)))) {
            writeJarEntries(jos);
        }

        ModifyJarManifest.main(new String[]{
                "--jar", jarFile.getAbsolutePath(),
                "--out", output.getAbsolutePath(),
                "--remove-entry", "Class-Path",
                "--add-entry", "Foo:Bar"});

        Assert.assertArrayEquals("jars expected to be identical",
                readAllBytes(jarFile.toPath()),
                readAllBytes(output.toPath()));
    }

    @Test
    public void modifyJar_removesEntry() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Class-Path", "bar.joo");
        manifest.getMainAttributes().putValue("Created-By", "ModifyJarManifest");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            writeJarEntries(jos);
        }

        ModifyJarManifest.main(new String[]{
                "--jar", jarFile.getPath(),
                "--out", output.getPath(),
                "--remove-entry", "Class-Path"});

        Attributes mainAttributes = new JarFile(output).getManifest().getMainAttributes();
        assertEquals("ModifyJarManifest",
                mainAttributes.getValue("Created-By"));
        assertFalse("jar manifest entry not removed",
                mainAttributes.containsKey(new Attributes.Name("Class-Path")));
    }

    @Test
    public void modifyJar_addsEntry() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            writeJarEntries(jos);
        }

        ModifyJarManifest.main(new String[]{
                "--jar", jarFile.getPath(),
                "--out", output.getPath(),
                "--add-entry", "Foo:Bar"});

        Attributes mainAttributes = new JarFile(output).getManifest().getMainAttributes();
        assertEquals("Bar",
                mainAttributes.getValue("Foo"));
    }

    private static void writeJarEntries(ZipOutputStream zos) throws IOException {
        ZipEntry jarEntry = new ZipEntry("foobar.class");
        zos.putNextEntry(jarEntry);
        zos.closeEntry();
    }
}
