package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.google.common.annotations.VisibleForTesting;

import java.util.List;

/**
 * The default Report implementation, which prints the Report Name in the format "MyTaskName
 * Report", the description (from {@link MemoryAnalyzerTask#getTaskDescription()}), and the table of
 * results.
 */
public final class DefaultReport implements Report {

    private final MemoryAnalyzerTask mTask;
    protected List<AnalysisResultEntry<?>> mResults;

    @VisibleForTesting
    protected static final String NO_ISSUES_FOUND_STRING = "No issues found.";

    public DefaultReport(@NonNull MemoryAnalyzerTask task) {
        mTask = task;
    }

    @Override
    public void generate(@NonNull List<AnalysisResultEntry<?>> data) {
        mResults = data;
    }

    /**
     * Prints the title of the report, the description, and the warning messages of all of the
     * entries.
     *
     * <p>This simple output is sufficient for many tasks, assuming their titles and warning
     * messages contain useful data.
     *
     * @param printer the {@link Printer} to print out to.
     */
    @Override
    public void print(@NonNull Printer printer) {
        printer.addHeading(
                2, (new StringBuilder()).append(mTask.getTaskName()).append(" Report").toString());

        printer.addParagraph(mTask.getTaskDescription());

        if (mResults != null && mResults.isEmpty()) {
            printer.addParagraph(NO_ISSUES_FOUND_STRING);
            return;
        }
        printer.startTable();
        for (AnalysisResultEntry<?> result : mResults) {
            printer.addRow(result.getWarningMessage());
        }
        printer.endTable();
    }

}
