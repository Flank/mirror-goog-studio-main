package com.android.tools.perflib.heap.memoryanalyzer;

import static org.junit.Assert.assertEquals;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public final class TopMemoryOffendersAnalyzerTaskTest {

    @Mock
    Snapshot mSnapshotMock;
    @Mock
    private Instance mMockInstance1;
    @Mock
    private Instance mMockInstance2;
    @Mock
    private Instance mMockInstance3;
    @Mock
    private Printer mPrinterMock;

    @Before
    public void setupMock() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTopMemoryOffendersReport() throws Exception {
        // arrange
        Mockito.when(mMockInstance1.getTotalRetainedSize()).thenReturn(140L);
        Mockito.when(mMockInstance2.getTotalRetainedSize()).thenReturn(139L);
        Mockito.when(mMockInstance3.getTotalRetainedSize()).thenReturn(138L);
        List<Instance> mockInstances = new ArrayList<>();
        mockInstances.add(mMockInstance2);
        mockInstances.add(mMockInstance1);
        mockInstances.add(mMockInstance3);
        Mockito.when(mSnapshotMock.getReachableInstances()).thenReturn(mockInstances);

        // act
        TopMemoryOffendersAnalyzerTask task = new TopMemoryOffendersAnalyzerTask();
        List<AnalysisResultEntry<?>> results = task
                .analyze(new MemoryAnalyzerTask.Configuration(Collections.emptyList()),
                        mSnapshotMock);

        // verify
        assertEquals(((Instance) results.get(0).getOffender().getOffenders().get(0))
                .getTotalRetainedSize(), 140L);
        assertEquals(((Instance) results.get(1).getOffender().getOffenders().get(0))
                .getTotalRetainedSize(), 139L);
        assertEquals(((Instance) results.get(2).getOffender().getOffenders().get(0))
                .getTotalRetainedSize(), 138L);
    }

}
