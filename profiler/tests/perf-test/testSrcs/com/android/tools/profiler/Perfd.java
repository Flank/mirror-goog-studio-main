package android.tools.profiler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;

public class Perfd {

    private static final String PERFD_PATH = ProcessRunner.getProcessPath("perfd.location");
    private static final int PORT = 12389;
    private static final String IP_ADDRESS = "127.0.0.1";

    private final ProcessRunner myRunner = new ProcessRunner(PERFD_PATH);

    public Perfd() {}

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
                ManagedChannelBuilder.forAddress(IP_ADDRESS, PORT).usePlaintext(true).build();
        Thread.currentThread().setContextClassLoader(stashedContextClassLoader);
        return channel;
    }
}
