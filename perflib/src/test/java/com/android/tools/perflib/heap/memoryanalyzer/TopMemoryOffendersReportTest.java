package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.memoryanalyzer.TopMemoryOffendersAnalyzerTask.TopMemoryOffendersEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public final class TopMemoryOffendersReportTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    TopMemoryOffendersEntry mEntryMock;
    @Mock
    private Instance mInstanceMock;
    @Mock
    private Printer mPrinterMock;

    @Before
    public void setupMock() {
        MockitoAnnotations.initMocks(this);
        Mockito.when((Object) mEntryMock.getOffender().getOffenders().get(0))
                .thenReturn(mInstanceMock);
        Mockito.when(mEntryMock.getOffender().getOffenders().size()).thenReturn(1);
        Mockito.when(mInstanceMock.getTotalRetainedSize()).thenReturn(140L);
        Mockito.when(mPrinterMock.formatInstance(mInstanceMock)).thenReturn("mock instance");
    }

    @Test
    public void testTopMemoryOffendersReport() throws Exception {
        // arrange
        List<AnalysisResultEntry<?>> entries = new ArrayList<>();
        entries.add(mEntryMock);

        // act
        TopMemoryOffendersReport report = new TopMemoryOffendersReport();
        report.generate(entries);
        report.print(mPrinterMock);

        // verify
        TopMemoryOffendersAnalyzerTask task = new TopMemoryOffendersAnalyzerTask();
        InOrder inOrder = Mockito.inOrder(mPrinterMock);
        inOrder.verify(mPrinterMock).addHeading(2, task.getTaskName() + " Report");
        inOrder.verify(mPrinterMock).addParagraph(task.getTaskDescription());
        inOrder.verify(mPrinterMock).addRow("140", "mock instance");
    }

    @Test
    public void testReportNotGenerated() throws Exception {
        // act
        (new TopMemoryOffendersReport()).print(mPrinterMock);

        // verify
        TopMemoryOffendersAnalyzerTask task = new TopMemoryOffendersAnalyzerTask();
        InOrder inOrder = Mockito.inOrder(mPrinterMock);
        inOrder.verify(mPrinterMock).addHeading(2, task.getTaskName() + " Report");
        inOrder.verify(mPrinterMock).addParagraph(task.getTaskDescription());
        inOrder.verify(mPrinterMock).addParagraph("Top offenders not found.");
    }
}
