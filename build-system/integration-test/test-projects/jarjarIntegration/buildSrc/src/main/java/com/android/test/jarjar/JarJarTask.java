package com.android.test.jarjar;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

public abstract class JarJarTask extends DefaultTask {

    @javax.inject.Inject
    public JarJarTask() {}

    @InputFiles
    abstract ListProperty<Directory> getInputDirectories();

    @InputFiles
    abstract ListProperty<RegularFile> getInputJars();

    @OutputFiles
    abstract RegularFileProperty getOutput();

    @TaskAction
    void taskAction() throws Exception {

        File mergedInputs = File.createTempFile("jajar", "jar");
        File jarjarRules = File.createTempFile("jajar", "rule");

        try {
            // create a tmp jar that contains all the inputs. This is because jarjar expects a jar
            // input.
            // this code is based on the JarMergingTransform
            combineInputIntoJar(mergedInputs);

            // create the jarjar rules file.
            Files.write("rule com.google.gson.** com.google.repacked.gson.@1", jarjarRules, Charsets.UTF_8);

            // run jarjar by calling the main method as if it came from the command line.
            String[] args =
                    ImmutableList.of(
                                    "process",
                                    jarjarRules.getAbsolutePath(),
                                    mergedInputs.getAbsolutePath(),
                                    getOutput().get().getAsFile().getAbsolutePath())
                            .toArray(new String[4]);
            com.tonicsystems.jarjar.Main.main(args);
        } finally {
            // delete tmp files
            mergedInputs.delete();
            jarjarRules.delete();
        }
    }

    private void combineInputIntoJar(File mergedInputs) throws IOException {
        Closer closer = Closer.create();
        try {

            FileOutputStream fos = closer.register(new FileOutputStream(mergedInputs));
            JarOutputStream jos = closer.register(new JarOutputStream(fos));

            final byte[] buffer = new byte[8192];

            for (Directory directory : getInputDirectories().get()) {
                processFolder(jos, "", directory.getAsFile(), buffer);
            }
            for (RegularFile regularFile : getInputJars().get()) {
                processJarFile(jos, regularFile.getAsFile(), buffer);
            }
        } finally {
            closer.close();
        }
    }

    private static void processFolder(JarOutputStream jos, String path, File folder, byte[] buffer)
            throws IOException {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // new entry
                    jos.putNextEntry(new JarEntry(path + file.getName()));

                    // put the file content
                    Closer closer = Closer.create();
                    try {
                        FileInputStream fis = closer.register(new FileInputStream(file));
                        int count;
                        while ((count = fis.read(buffer)) != -1) {
                            jos.write(buffer, 0, count);
                        }
                    } finally {
                        closer.close();
                    }

                    // close the entry
                    jos.closeEntry();
                } else if (file.isDirectory()) {
                    processFolder(jos, path + file.getName() + "/", file, buffer);
                }
            }
        }
    }

    private static void processJarFile(JarOutputStream jos, File file, byte[] buffer)
            throws IOException {

        Closer closer = Closer.create();
        try {
            FileInputStream fis = closer.register(new FileInputStream(file));
            ZipInputStream zis = closer.register(new ZipInputStream(fis));

            // loop on the entries of the jar file package and put them in the final jar
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // do not take directories or anything inside a potential META-INF folder.
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }

                // add the entry to the jar archive
                jos.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    jos.write(buffer, 0, count);
                }

                // close the entries for this file
                jos.closeEntry();
                zis.closeEntry();
            }
        } finally {
            closer.close();
        }
    }
}
