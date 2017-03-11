package com.example.bytecode.plugins;

import com.google.common.io.ByteStreams;
import java.io.*;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** A task that generates bytecode simply by extracting it from a jar. */
public class BytecodeGeneratingTask extends DefaultTask {

    private File sourceJar;
    private FileCollection classpath;
    private File outputDir;

    @InputFile
    public File getSourceJar() {
        return sourceJar;
    }

    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setSourceJar(File sourceJar) {
        this.sourceJar = sourceJar;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    void generate() throws IOException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new RuntimeException("Failed to mkdirs: " + outputDir);
        }

        try (InputStream fis = new BufferedInputStream(new FileInputStream(sourceJar));
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    String name = entry.getName();

                    // do not take directories or non class files
                    if (entry.isDirectory() || !name.endsWith(".class")) {
                        continue;
                    }

                    File outputFile = new File(outputDir, name.replace('/', File.separatorChar));

                    final File parentFile = outputFile.getParentFile();
                    if (!parentFile.exists() && !parentFile.mkdirs()) {
                        throw new RuntimeException("Failed to mkdirs: " + parentFile);
                    }

                    try (OutputStream outputStream =
                            new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        ByteStreams.copy(zis, outputStream);
                        outputStream.flush();
                    }
                } finally {
                    zis.closeEntry();
                }
            }
        }

        // check the compile classpath
        Set<File> files = classpath.getFiles();
        for (File file : files) {
            if (!file.exists()) {
                throw new RuntimeException("Dependency file does not exist: " + file);
            }
            // prints the content so that the test can validate it.
            System.out.println(
                    "BytecodeGeneratingTask("
                            + getProject().getPath()
                            + ":"
                            + getName()
                            + "): "
                            + file);
        }
    }
}
