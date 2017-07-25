package com.android.tools.profiler;

public class PerfdDriver extends ProcessRunner {

    private static final String PERFD_PATH = ProcessRunner.getProcessPath("perfd.location");

    public PerfdDriver(String configFilePath) {
        super(PERFD_PATH, "-config_file=" + configFilePath);
    }
}
