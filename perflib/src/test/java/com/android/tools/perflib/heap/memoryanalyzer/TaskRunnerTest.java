package com.android.tools.perflib.heap.memoryanalyzer;

import static org.junit.Assert.assertEquals;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link TaskRunner}.
 */
@RunWith(JUnit4.class)
public class TaskRunnerTest {

    @Mock
    private Snapshot mSnapshotMock;

    @Before
    public void setUpMocks() {
        MockitoAnnotations.initMocks(this);
        Mockito.when(mSnapshotMock.getTypeName()).thenReturn(Snapshot.TYPE_NAME);
        Mockito.when(mSnapshotMock.getRepresentation(Snapshot.class)).thenReturn(mSnapshotMock);
        Mockito.when(mSnapshotMock.getHeaps()).thenReturn(Collections.<Heap>emptyList());
    }

    @Test
    public void runTasksShouldGenerateEntry() throws InterruptedException {
        // arrange
        Set<MemoryAnalyzerTask> tasks = new HashSet<>();
        tasks.add(new BasicAnalyzerTask());

        // act
        List<AnalysisResultEntry<?>> content = TaskRunner.runTasks(tasks, mSnapshotMock);

        // assert
        assertEquals(1, content.size());
        assertEquals(BasicAnalyzerTask.TASK_WARNING, content.get(0).getWarningMessage());
    }

    @Test
    public void runTasksShouldGenerateEntries_multipleCapture() throws InterruptedException {
        // arrange
        Set<MemoryAnalyzerTask> tasks = new HashSet<>();
        tasks.add(new BasicAnalyzerTask());
        // act
        List<AnalysisResultEntry<?>> content = TaskRunner
                .runTasks(tasks, mSnapshotMock, mSnapshotMock);

        // assert
        assertEquals(2, content.size());
        assertEquals(BasicAnalyzerTask.TASK_WARNING, content.get(0).getWarningMessage());
    }
}
