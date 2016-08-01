package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gets the top memory offenders from a Snapshot in sorted order by retained size.
 */
public final class TopMemoryOffendersAnalyzerTask extends MemoryAnalyzerTask {

    private static final int DEFAULT_NUM_ENTRIES = 5;

    private final int mNumEntries;

    public TopMemoryOffendersAnalyzerTask() {
        this(DEFAULT_NUM_ENTRIES);
    }

    public TopMemoryOffendersAnalyzerTask(int numEntries) {
        mNumEntries = numEntries;
    }

    @Override
    protected List<AnalysisResultEntry<?>> analyze(Configuration configuration, Snapshot snapshot) {
        List<Instance> reachableInstances = snapshot.getReachableInstances();
        Collections.sort(reachableInstances,
                (a, b) -> Long.compare(a.getTotalRetainedSize(), b.getTotalRetainedSize()));
        Collections.reverse(reachableInstances);

        List<AnalysisResultEntry<?>> entries = new ArrayList<>();
        for (Instance instance : reachableInstances
                .subList(0, Math.min(mNumEntries, reachableInstances.size()))) {
            entries.add(
                    new TopMemoryOffendersEntry(
                            "Offender #" + (entries.size() + 1) + " (" + instance
                                    + ") has total retained size " + instance.getTotalRetainedSize()
                                    + ".",
                            Arrays.asList(instance)));
        }
        return entries;
    }

    @Override
    public String getTaskName() {
        return "Top Memory Offenders";
    }

    @Override
    public String getTaskDescription() {
        return "Finds the top objects in memory.";
    }

    public static class TopMemoryOffendersEntry extends MemoryAnalysisResultEntry {

        protected TopMemoryOffendersEntry(
                String offenseDescription, List<Instance> offendingInstances) {
            super(offenseDescription, offendingInstances);
        }

        @Override
        public String getWarningMessage() {
            return mOffender.getOffendingDescription();
        }

        @Override
        public String getCategory() {
            return "Top Memory Offenders";
        }
    }
}
