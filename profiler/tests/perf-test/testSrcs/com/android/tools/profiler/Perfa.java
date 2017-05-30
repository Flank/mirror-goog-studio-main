package android.tools.profiler;

import java.io.IOException;

public class Perfa {

    private static final String ART_PATH = ProcessRunner.getProcessPath("art.location");
    private static final String[] ART_ARGS = {
        ART_PATH,
        "--64",
        "--verbose",
        "-Djava.library.path=" + ProcessRunner.getProcessPath("agent.location"),
        "-cp",
        ProcessRunner.getProcessPath("perfa.dex.location")
                + ":"
                + ProcessRunner.getProcessPath("android-mock.dex.location"),
        "com.android.tools.profiler.Main"
    };

    private final ProcessRunner myRunner = new ProcessRunner(ART_ARGS);

    public Perfa() {}

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
