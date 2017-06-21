package android.tools.profiler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;

public class Perfd {

    private static final String PERFD_PATH = ProcessRunner.getProcessPath("perfd.location");
    private final int myPort;
    private static final String IP_ADDRESS = "127.0.0.1";

    private final ProcessRunner myRunner;

    public Perfd(int port, String configFilePath) {
        myPort = port;
        String[] perfdArgs = {PERFD_PATH, "ConfigFile=" + configFilePath};
        myRunner = new ProcessRunner(perfdArgs);
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

    /** @return channel used to connect to perfd GRPC services. */
    public ManagedChannel connectGrpc() {
        ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ManagedChannelBuilder.class.getClassLoader());
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress(IP_ADDRESS, myPort).usePlaintext(true).build();
        Thread.currentThread().setContextClassLoader(stashedContextClassLoader);
        return channel;
    }
}
