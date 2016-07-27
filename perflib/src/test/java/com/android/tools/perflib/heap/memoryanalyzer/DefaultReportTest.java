package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.memoryanalyzer.BasicAnalyzerTask.BasicResultEntry;

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

/**
 * Tests for {@link Report}.
 */
@RunWith(JUnit4.class)
public class DefaultReportTest {

    @Mock
    private Printer mPrinterMock;

    @Before
    public void setUpMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPrintFormatting_dataEmpty() {
        // arrange
        List<AnalysisResultEntry<?>> data = Collections.emptyList();
        Report report = new DefaultReport(new BasicAnalyzerTask());
        report.generate(data);

        // act
        report.print(mPrinterMock);

        // verify
        Mockito.verify(mPrinterMock)
                .addParagraph(DefaultReport.NO_ISSUES_FOUND_STRING);
    }

    @Test
    public void testPrintFormatting_dataGenerated() {
        // arrange
        List<AnalysisResultEntry<?>> data = new ArrayList<>();
        data.add(new BasicResultEntry());
        Report report = new DefaultReport(new BasicAnalyzerTask());
        report.generate(data);

        // act
        report.print(mPrinterMock);

        // verify
        Mockito.verify(mPrinterMock)
                .addHeading(2, BasicAnalyzerTask.TASK_NAME + " Report");
        Mockito.verify(mPrinterMock)
                .addParagraph(BasicAnalyzerTask.TASK_DESCRIPTION);
        Mockito.verify(mPrinterMock)
                .addRow(BasicAnalyzerTask.TASK_WARNING);
    }
}
