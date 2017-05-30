package android.tools.profiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;

public class ProcessRunner implements Runnable {

    private String[] myProcessArgs;
    private Process myProcess;
    private List<String> myProcessOutput = new ArrayList();
    private Thread myRunningThread;

    public static String getProcessPath(String property) {
        return System.getProperty("user.dir") + File.separator + System.getProperty(property);
    }

    protected ProcessRunner(String... processArgs) {
        myProcessArgs = processArgs;
    }

    public void start() throws IOException {
        System.out.println("Starting " + myProcessArgs[0]);
        myProcess = Runtime.getRuntime().exec(myProcessArgs);

        // Thread to capture the process output.
        myRunningThread = new Thread(this);
        myRunningThread.start();
    }

    /** @return true if the process is created and alive. */
    public boolean isAlive() {
        return myProcess != null && myProcess.isAlive();
    }

    @Override
    public void run() {
        try {
            InputStream stdin = myProcess.getErrorStream();
            InputStreamReader isr = new InputStreamReader(stdin);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while (br.ready() && (line = br.readLine()) != null) {
                myProcessOutput.add(line);
            }
        } catch (IOException ex) {
            Assert.fail("Unexpected process exception: " + ex);
        } catch (Exception ex) {
            dumpOuput();
        }
    }

    public void stop() {
        try {
            myProcess.destroy();
            myProcess.waitFor();
            myRunningThread.join(100);
            myRunningThread.interrupt();
        } catch (InterruptedException ex) {
            // Do nothing.
        }
    }

    public void dumpOuput() {
        String procname = myProcessArgs[0].substring(myProcessArgs[0].lastIndexOf("/") + 1);
        for (String string : myProcessOutput) {
            System.out.printf("[%s]: %s\n", procname, string);
        }
    }
}
