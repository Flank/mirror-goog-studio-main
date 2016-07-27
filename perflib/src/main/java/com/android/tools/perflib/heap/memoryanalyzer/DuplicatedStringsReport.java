package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Instance;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A report to organize the results of perflib's {@link DuplicatedStringsAnalyzerTask}.
 *
 * <p>Specifically, this report sorts the results by the effective size of the duplicated string:
 * the length of the string times the number of occurrences. Additionally, when printed, it creates
 * a detailed table including the string value, number of occurrences, total consumed size, and an
 * example duplicate instance.
 */
public final class DuplicatedStringsReport implements Report {

    private List<AnalysisResultEntry<?>> mResults;

    // Limits the number of characters of the duplicated string is shown.
    private static final int MAX_VALUE_STRING_LENGTH = 100;

    @Override
    public void generate(@NonNull List<AnalysisResultEntry<?>> results) {
        // Sort by how many bytes each set of duplicates is actually occupying.
        Collections.sort(
                results,
                Collections.reverseOrder(
                        new Comparator<AnalysisResultEntry<?>>() {
                            @Override
                            public int compare(AnalysisResultEntry<?> o1,
                                    AnalysisResultEntry<?> o2) {
                                return getConsumedBytes(o1) - getConsumedBytes(o2);
                            }
                        }));

        mResults = results;
    }

    @Override
    public void print(@NonNull Printer printer) {
        DuplicatedStringsAnalyzerTask task = new DuplicatedStringsAnalyzerTask();
        printer.addHeading(2, task.getTaskName() + " Report");
        printer.addParagraph(task.getTaskDescription());

        if (mResults == null || mResults.isEmpty()) {
            printer.addParagraph("No issues found.");
            return;
        }

        printer.startTable("Value", "Bytes", "Duplicates", "First Duplicate");
        for (AnalysisResultEntry<?> entry : mResults) {
            String value = entry.getOffender().getOffendingDescription();
            if (value.length() > MAX_VALUE_STRING_LENGTH) {
                value = value.substring(0, MAX_VALUE_STRING_LENGTH) + "...";
            }
            String consumedBytes = Integer.toString(getConsumedBytes(entry));
            String duplicates = Integer.toString(entry.getOffender().getOffenders().size());
            String instance =
                    printer.formatInstance((Instance) entry.getOffender().getOffenders().get(0));
            printer.addRow(value, consumedBytes, duplicates, instance);
        }
        printer.endTable();
    }

    private int getConsumedBytes(@NonNull AnalysisResultEntry<?> entry) {
        return entry.getOffender().getOffendingDescription().length()
                * entry.getOffender().getOffenders().size();
    }
}
