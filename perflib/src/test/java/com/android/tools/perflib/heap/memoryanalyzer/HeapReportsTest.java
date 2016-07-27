package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link HeapReports}.
 */
@RunWith(JUnit4.class)
public class HeapReportsTest {

    @Mock
    private Snapshot mSnapshotMock;
    @Mock
    private Report mReportMock;

    @Before
    public void setUpMocks() {
        MockitoAnnotations.initMocks(this);
        Mockito.when(mSnapshotMock.getTypeName()).thenReturn(Snapshot.TYPE_NAME);
        Mockito.when(mSnapshotMock.getRepresentation(Snapshot.class)).thenReturn(mSnapshotMock);
        Mockito.when(mSnapshotMock.getHeaps()).thenReturn(new ArrayList<Heap>());
    }

    @Test
    public void analyzeGeneratesData() {
        // act
        HeapReports.generateReport(mReportMock, new BasicAnalyzerTask(), mSnapshotMock);

        // assert
        Mockito.verify(mReportMock).generate(Matchers.<List<AnalysisResultEntry<?>>>any());
    }
}
