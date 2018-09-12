import com.android.tools.perflogger.Benchmark;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FoundryPerfgateMetrics {

    private static Benchmark benchmark = new Benchmark.Builder("Foundry Baseline Tests").build();

    @Test
    public void testPerfgateCPUTest() {
        System.out.println("testPerfgateCPUTest");
        long foo = 0; // arbitrary variable for arbitrary CPU loop
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) for (int j = 0; j < 5000; j++) foo += Math.tan(i + j);
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("elapsedTime: " + elapsedTime);
        benchmark.log("cpu_test", elapsedTime);
    }

    private static void doubleCopyFile(File src, File dst) throws IOException {
        try (InputStream is = new FileInputStream(src);
                OutputStream os = new FileOutputStream(dst)) {
            byte[] buffer = new byte[2560];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
                os.write(buffer, 0, length);
                os.flush();
            }
        }
    }

    @Test
    public void testPerfgateDiskTest() throws IOException {
        System.out.println("testPerfgateDiskTest");
        long startTime = System.currentTimeMillis();
        File src = File.createTempFile("foo", ".out");
        FileWriter writer = new FileWriter(src);
        writer.write("foobar baz foo bazfoobaz bar");
        writer.close();
        for (int i = 0; i < 7; i++) {
            File dst = File.createTempFile("bar", ".out");
            doubleCopyFile(src, dst);
            doubleCopyFile(dst, src);
            dst.delete();
        }
        src.delete();
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("elapsedTime: " + elapsedTime);
        benchmark.log("disk_test", elapsedTime);
    }

    private static long getGCTotalTime() {
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = gc.getCollectionTime();
            if (time >= 0) gcTime += time;
        }
        return gcTime;
    }

    private static long getGCTotalCount() {
        long gcCount = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count >= 0) gcCount += count;
        }
        return gcCount;
    }

    @Test
    public void testPerfgateMemoryTest() {
        long gcTimeBefore = getGCTotalTime();
        long gcCountBefore = getGCTotalCount();
        int byteArraySize = 1024 * 1024 * 12;
        int numArrays = 512;
        System.out.println("testPerfgateMemoryTest");
        byte[][] v = new byte[numArrays][];
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numArrays; i++) {
            // Allocate a large byte array and keep it around to avoid GC
            byte b[] = new byte[byteArraySize];
            v[i] = b;
            if (i % 200 == 0)
                System.out.println("free memory: " + Runtime.getRuntime().freeMemory());
        }
        long elapsedTime = System.currentTimeMillis() - startTime;
        long gcElapsedTime = getGCTotalTime() - gcTimeBefore;
        long gcCount = getGCTotalCount() - gcCountBefore;
        System.out.println("gcTime: " + gcElapsedTime);
        System.out.println("gcCount: " + gcCount);
        System.out.println("elapsedTime: " + elapsedTime);
        benchmark.log("memory_test", elapsedTime);
        benchmark.log("memory_test_gc", gcElapsedTime);
        benchmark.log("memory_test_nogc", (elapsedTime - gcElapsedTime));
        benchmark.log("memory_test_gc_count", gcCount);
    }
}
