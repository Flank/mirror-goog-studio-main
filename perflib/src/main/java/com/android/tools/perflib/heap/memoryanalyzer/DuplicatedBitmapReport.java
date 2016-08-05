package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.memoryanalyzer.DuplicatedBitmapAnalyzerTask.DuplicatedBitmapEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Displays the largest items in memory.
 */
public final class DuplicatedBitmapReport implements Report {

    private List<AnalysisResultEntry<?>> results;

    @Override
    public void generate(List<AnalysisResultEntry<?>> data) {
        List<DuplicatedBitmapEntry> bitmapEntries = new ArrayList<>();
        for (AnalysisResultEntry<?> entry : data) {
            if (entry instanceof DuplicatedBitmapEntry) {
                bitmapEntries.add((DuplicatedBitmapEntry) entry);
            } else {
                this.results = null;
                return;
                // TODO(gusss) make Report#generate throw IllegalArgException.
                //throw new IllegalArgumentException("AnalysisResultEntry which is not an"
                //    + " instance of DuplicatedBitmapEntry found in report. Make sure you are "
                //    + " generating this report with results from a"
                //    + " DuplicatedBitmapAnalyzerTask.", e);
            }
        }

        // Sort by how many bytes each set of duplicates is actually occupying.
        Collections.sort(
                bitmapEntries,
                Collections.reverseOrder(
                        (DuplicatedBitmapEntry o1, DuplicatedBitmapEntry o2) -> getConsumedBytes(o1)
                                - getConsumedBytes(o2)));
        results = new ArrayList<>();
        results.addAll(bitmapEntries);
    }

    @Override
    public void print(Printer printer) {
        DuplicatedBitmapAnalyzerTask task = new DuplicatedBitmapAnalyzerTask();
        printer.addHeading(2, task.getTaskName() + " Report");
        printer.addParagraph(task.getTaskDescription());

        if (results == null || results.isEmpty()) {
            printer.addParagraph("No issues found.");
            return;
        }

        for (AnalysisResultEntry<?> entry : results) {
            if (entry.getOffender().getOffenders().size() < 1) {
                continue;
            }
            if (!(entry instanceof DuplicatedBitmapEntry)) {
                continue;
            }
            Instance firstInstance = (Instance) entry.getOffender().getOffenders().get(0);
            printer.addHeading(3, printer.formatInstance(firstInstance));
            printer.addImage(firstInstance);
            int size = ((DuplicatedBitmapEntry) entry).getByteArraySize();
            String bytes = Integer.toString(size);
            int duplicates = entry.getOffender().getOffenders().size();
            printer.startTable("Bytes", "Duplicates", "Total Bytes Consumed");
            printer.addRow(bytes, Integer.toString(duplicates),
                    Integer.toString(size * duplicates));
            printer.endTable();

            printer.startTable("All Duplicates");
            List<Instance> instances = (List<Instance>) entry.getOffender().getOffenders();
            for (Instance instance : instances) {
                printer.addRow(printer.formatInstance(instance));
            }
            printer.endTable();
        }
    }

    private static int getConsumedBytes(DuplicatedBitmapEntry entry) {
        return entry.getByteArraySize() * entry.getOffender().getOffenders().size();
    }
}
