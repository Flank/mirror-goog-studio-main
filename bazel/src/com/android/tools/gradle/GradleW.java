package com.android.tools.gradle;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;

class GradleW {

    public static void main(String[] args) throws IOException, InterruptedException {
        System.exit(new GradleW().run(Arrays.asList(args), System.getenv("GRADLE_USER_HOME")));
    }

    private int run(List<String> args, String gradleUserHomePath) throws IOException {
        File outFile = null;
        File gradleFile = null;
        String outPath = null;
        File distribution = null;
        LinkedList<File> repos = new LinkedList<>();
        LinkedList<String> tasks = new LinkedList<>();

        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("--out_file") && it.hasNext()) {
                outFile = new File(it.next());
            } else if (arg.equals("--gradle_file") && it.hasNext()) {
                gradleFile = new File(it.next());
            } else if (arg.equals("--out_path") && it.hasNext()) {
                outPath = it.next();
            } else if (arg.equals("--distribution") && it.hasNext()) {
                distribution = new File(it.next());
            } else if (arg.equals("--repo") && it.hasNext()) {
                repos.add(new File(it.next()));
            } else if (arg.equals("--repo") && it.hasNext()) {
                repos.add(new File(it.next()));
            } else if (arg.equals("--task") && it.hasNext()) {
                tasks.add(it.next());
            }
        }
        File gradleUserHome = new File(gradleUserHomePath).getAbsoluteFile();
        gradlew(outFile, outPath, gradleFile, tasks, repos, distribution, gradleUserHome);
        return 0;
    }

    public void gradlew(
            File outFile,
            String outPath,
            File gradleFile,
            List<String> tasks,
            List<File> repos,
            File distribution,
            File gradleUserHome)
            throws IOException {
        File outDir = new File(outFile.getParentFile(), outFile.getName() + ".temp2");
        FileUtils.cleanOutputDir(outDir);
        try {
            File buildDir = new File(outDir, "_build").getAbsoluteFile();
            Files.createDirectories(buildDir.toPath());
            File androidDir = new File(outDir, "_android").getAbsoluteFile();
            File expectedGradleUserHome = new File(outDir, "_home").getAbsoluteFile();
            if (!expectedGradleUserHome
                    .getAbsolutePath()
                    .equals(gradleUserHome.getAbsolutePath())) {
                throw new RuntimeException(
                        "GRADLE_USER_HOME should be outFile + .temp2/home\n"
                                + "GRADLE_USER_HOME = "
                                + gradleUserHome.getAbsolutePath()
                                + "\nexpected: "
                                + expectedGradleUserHome.getAbsolutePath());
            }
            File repoDir = new File(outDir, "_repo").getAbsoluteFile();
            File initScript = new File(outDir, "init.script").getAbsoluteFile();
            // gradle tries to write into .m2 so we pass it a tmp one.
            File tmpLocalMaven = new File(outDir, "_tmp_local_maven").getAbsoluteFile();
            createInitScript(initScript, repoDir);
            for (File repo : repos) {
                unzip(repo, repoDir);
            }

            HashMap<String, String> env = new HashMap<>();

            env.put("ANDROID_HOME", new File(TestUtils.getRelativeSdk()).getAbsolutePath());
            env.put("BUILD_DIR", buildDir.getAbsolutePath());
            env.put("ANDROID_SDK_HOME", androidDir.getAbsolutePath());

            // On windows it is needed to set a few more environment variables
            env.put("SystemRoot", System.getenv("SystemRoot"));
            env.put("TEMP", System.getenv("TEMP"));
            env.put("TMP", System.getenv("TMP"));

            ProjectConnection projectConnection =
                    getProjectConnection(gradleUserHome, gradleFile.getParentFile(), distribution);
            try {
                BuildLauncher launcher =
                        projectConnection
                                .newBuild()
                                .setEnvironmentVariables(env)
                                .withArguments(
                                        "--offline",
                                        "--init-script",
                                        initScript.getAbsolutePath(),
                                        "-Dmaven.repo.local=" + tmpLocalMaven.getAbsolutePath())
                                .forTasks(tasks.toArray(new String[0]));
                launcher.setStandardOutput(System.out);
                launcher.setStandardError(System.err);
                launcher.run();
            } finally {
                projectConnection.close();
                // Shut down the daemon so it doesn't hold the lock on any of the files.
                // Note that this is internal Gradle API, but is used by Studio and Intellij so is
                // relatively stable.
                // This depends on the GRADLE_USER_HOME environment variable.
                DefaultGradleConnector.close();
            }
            File source = new File(buildDir, outPath);
            Files.copy(source.toPath(), outFile.toPath());
        } finally {
            // TODO: Maybe allow this to fail, as it will be cleaned up by the next
            FileUtils.cleanOutputDir(outDir);
        }
    }

    private static void createInitScript(File initScript, File repoDir) throws IOException {
        String content =
                "allprojects {\n"
                        + "  buildscript {\n"
                        + "    repositories {\n"
                        + "       maven { url '"
                        + repoDir.toURI().toString()
                        + "'}\n"
                        + "    }\n"
                        + "  }\n"
                        + "  repositories {\n"
                        + "       maven { url '"
                        + repoDir.toURI().toString()
                        + "'}\n"
                        + "  }\n"
                        + "}\n";

        try (FileWriter writer = new FileWriter(initScript)) {
            writer.write(content);
        }
    }

    private static void unzip(File zip, File out) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                File newFile = new File(out, fileName);
                newFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }

    @NonNull
    private static ProjectConnection getProjectConnection(
            File home, File projectDirectory, File distribution) throws IOException {
        GradleConnector connector =
                GradleConnector.newConnector()
                        .useDistribution(distribution.toURI())
                        .useGradleUserHomeDir(home)
                        .forProjectDirectory(projectDirectory);
        return connector.connect();
    }
}
