package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.testutils.TestResources;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.Snapshot;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DuplicatedBitmapAnalyzerTaskTest {

    private Snapshot mSnapshot = null;

    @Before
    public void getSnapshot() throws IOException {
        File file = TestResources.getFile(getClass(), "/duplicated_bitmaps.android-hprof");
        mSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
        mSnapshot.computeDominators();
        mSnapshot.resolveClasses();
    }

    @Test
    public void testDuplicatedBitmapAnalyzerTask() throws Exception {
        // arrange
        Set<MemoryAnalyzerTask> tasks = new HashSet<>();
        tasks.add(new DuplicatedBitmapAnalyzerTask());

        // act
        List<AnalysisResultEntry<?>> returnedEntries = TaskRunner.runTasks(tasks, mSnapshot);

        // assert
        Assert.assertEquals(returnedEntries.size(), 7);
    }

    @After
    public void dispose() {
        mSnapshot.dispose();
    }
}
