package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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
        Mockito.verify(mReportMock).generate(ArgumentMatchers.any());
    }
}
