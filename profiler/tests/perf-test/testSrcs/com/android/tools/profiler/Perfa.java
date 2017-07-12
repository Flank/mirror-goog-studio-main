package android.tools.profiler;

import java.io.IOException;
public class Perfa {

    private static final String ART_PATH = ProcessRunner.getProcessPath("art.location");
    private final ProcessRunner myRunner;

    public Perfa(String configPath) {
        myRunner =
                new ProcessRunner(
                        ART_PATH,
                        "--64",
                        "--verbose",
                        "-Djava.library.path=" + ProcessRunner.getProcessPath("agent.location"),
                        "-Dconfig.path=" + configPath,
                        "-cp",
                        ProcessRunner.getProcessPath("perfa.dex.location")
                                + ":"
                                + ProcessRunner.getProcessPath("android-mock.dex.location"),
                        "com.android.tools.profiler.Main");
    }

    public void start() throws IOException {
        myRunner.start();
    }

    public void stop() {
        myRunner.stop();
    }

    public boolean isAlive() {
        return myRunner.isAlive();
    }
}
