package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.testutils.TestResources;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class DuplicatedBitmapReportTest {

    @Mock
    private Printer mPrinterMock;

    @Before
    public void setupMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPrintFormatting_dataEmpty() {
        // arrange
        MemoryAnalyzerTask task = new DuplicatedBitmapAnalyzerTask();
        List<AnalysisResultEntry<?>> data = Collections.emptyList();
        Report report = new DuplicatedBitmapReport();
        report.generate(data);

        // act
        report.print(mPrinterMock);

        // assert
        InOrder inOrder = Mockito.inOrder(mPrinterMock);
        inOrder.verify(mPrinterMock).addHeading(2, task.getTaskName() + " Report");
        inOrder.verify(mPrinterMock).addParagraph(task.getTaskDescription());
        inOrder.verify(mPrinterMock).addParagraph(Mockito.contains("No issues found."));
    }

    @Test
    public void testPrintFormatting_dataGenerated() throws Exception {
        // arrange
        File file = TestResources.getFile(getClass(), "/duplicated_bitmaps.android-hprof");
        Snapshot snapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
        snapshot.computeDominators();
        snapshot.resolveClasses();
        DuplicatedBitmapAnalyzerTask task = new DuplicatedBitmapAnalyzerTask();
        DuplicatedBitmapReport report = new DuplicatedBitmapReport();
        HeapReports.generateReport(report, task, snapshot);

        // act
        report.print(mPrinterMock);

        // assert
        InOrder inOrder = Mockito.inOrder(mPrinterMock);

        inOrder.verify(mPrinterMock).addHeading(2, task.getTaskName() + " Report");
        inOrder.verify(mPrinterMock).addParagraph(task.getTaskDescription());

        inOrder.verify(mPrinterMock).addImage(Mockito.any(Instance.class));

        inOrder.verify(mPrinterMock).startTable("Bytes", "Duplicates", "Total Bytes Consumed");
        inOrder.verify(mPrinterMock, Mockito.atLeastOnce())
                .addRow(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        inOrder.verify(mPrinterMock).endTable();
        inOrder.verify(mPrinterMock).startTable("All Duplicates");
        inOrder.verify(mPrinterMock, Mockito.atLeastOnce())
                .addRow(Mockito.anyString());
        inOrder.verify(mPrinterMock).endTable();

        snapshot.dispose();
    }
}
