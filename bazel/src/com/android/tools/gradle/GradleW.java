package com.android.tools.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class GradleW {

    public static void main(String[] args) throws IOException {
        System.exit(new GradleW().run(Arrays.asList(args)));
    }

    private int run(List<String> args) throws IOException {
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

        File outDir = new File(outFile.getParentFile(), outFile.getName() + ".temp");
        outDir.mkdirs();
        try (Gradle gradle = new Gradle(gradleFile.getParentFile(), outDir, distribution)) {
            for (File repo : repos) {
                gradle.addRepo(repo);
            }
            gradle.run(tasks, System.out, System.err);

            File source = gradle.getOutput(outPath);
            Files.copy(source.toPath(), outFile.toPath());
        }

        return 0;
    }
}
