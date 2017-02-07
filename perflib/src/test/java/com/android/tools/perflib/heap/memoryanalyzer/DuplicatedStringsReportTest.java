package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.memoryanalyzer.DuplicatedStringsAnalyzerTask.DuplicatedStringsEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DuplicatedStringsReport}.
 */
@RunWith(JUnit4.class)
public final class DuplicatedStringsReportTest {

    private DuplicatedStringsEntry mEntry1;
    private DuplicatedStringsEntry mEntry2;
    private Instance mMockInstance;

    @Mock
    private Printer mPrinterMock;

    @Before
    public void setupMocks() {
        MockitoAnnotations.initMocks(this);

        mMockInstance = Mockito.mock(Instance.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mMockInstance.toString()).thenReturn("mockInstance");

        mEntry1 = Mockito.mock(DuplicatedStringsEntry.class, Mockito.RETURNS_DEEP_STUBS);
        mEntry2 = Mockito.mock(DuplicatedStringsEntry.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mEntry1.getOffender().getOffendingDescription())
                .thenReturn("offending string 1");
        Mockito.when(mEntry2.getOffender().getOffendingDescription())
                .thenReturn("offending string 2");
        Mockito.when(mEntry1.getOffender().getOffenders().size()).thenReturn(1);
        Mockito.when(mEntry2.getOffender().getOffenders().size()).thenReturn(2);
        Mockito.when((Object) mEntry1.getOffender().getOffenders().get(0))
                .thenReturn(mMockInstance);
        Mockito.when((Object) mEntry2.getOffender().getOffenders().get(0))
                .thenReturn(mMockInstance);
    }

    @Test
    public void testDuplicatedStringsReport() throws Exception {
        // arrange
        List<AnalysisResultEntry<?>> entries = new ArrayList<>();
        entries.add(mEntry1);
        entries.add(mEntry2);

        // act
        DuplicatedStringsReport report = new DuplicatedStringsReport();
        report.generate(entries);
        report.print(mPrinterMock);

        // verify
        DuplicatedStringsAnalyzerTask task = new DuplicatedStringsAnalyzerTask();
        InOrder inOrder = Mockito.inOrder(mPrinterMock);
        inOrder.verify(mPrinterMock).addHeading(2, task.getTaskName() + " Report");
        inOrder.verify(mPrinterMock).addParagraph(task.getTaskDescription());
        // verify that the entries were sorted correctly
        inOrder.verify(mPrinterMock)
                .addRow(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.eq("2"),
                        ArgumentMatchers.nullable(String.class));
        inOrder.verify(mPrinterMock)
                .addRow(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.eq("1"),
                        ArgumentMatchers.nullable(String.class));
    }

    @Test
    public void testPrintFormatting_dataEmpty() {
        // arrange
        DuplicatedStringsAnalyzerTask task = new DuplicatedStringsAnalyzerTask();
        List<AnalysisResultEntry<?>> data = Collections.emptyList();
        Report report = new DuplicatedStringsReport();
        report.generate(data);

        // act
        report.print(mPrinterMock);

        // verify
        Mockito.verify(mPrinterMock).addHeading(2, task.getTaskName() + " Report");
        Mockito.verify(mPrinterMock).addParagraph(task.getTaskDescription());
        Mockito.verify(mPrinterMock).addParagraph(Mockito.contains("No issues found."));
    }
}
