package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Instance;
import java.util.List;

/**
 * Displays the largest items in memory.
 */
public final class TopMemoryOffendersReport implements Report {

    private List<AnalysisResultEntry<?>> mResults;

    @Override
    public void generate(List<AnalysisResultEntry<?>> data) {
        mResults = data;
    }

    @Override
    public void print(Printer printer) {
        TopMemoryOffendersAnalyzerTask task = new TopMemoryOffendersAnalyzerTask();
        printer.addHeading(2, task.getTaskName() + " Report");
        printer.addParagraph(task.getTaskDescription());

        if (mResults == null || mResults.isEmpty()) {
            printer.addParagraph("Top offenders not found.");
            return;
        }

        printer.startTable("Total Retained Size", "Instance");
        for (AnalysisResultEntry<?> entry : mResults) {
            if (!entry.getOffender().getOffenders().isEmpty()) {
                Instance instance = (Instance) entry.getOffender().getOffenders().get(0);
                String totalRetainedSize = Long.toString(instance.getTotalRetainedSize());
                String instanceString = printer.formatInstance(instance);
                printer.addRow(totalRetainedSize, instanceString);
            }
        }
        printer.endTable();
    }
}
