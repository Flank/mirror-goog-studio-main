package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.Snapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Dummy task for testing.
 */
class BasicAnalyzerTask extends MemoryAnalyzerTask {

    protected static final String TASK_WARNING = "test warning";
    protected static final String TEST_CATEGORY = "test category";
    protected static final String TASK_NAME = "Basic Analyzer Task";
    protected static final String TASK_DESCRIPTION = "Basic Analyzer Task";

    @Override
    protected List<AnalysisResultEntry<?>> analyze(Configuration configuration, Snapshot snapshot) {
        List<AnalysisResultEntry<?>> list = new ArrayList<>();
        list.add(new BasicResultEntry());
        return list;
    }

    @Override
    public String getTaskName() {
        return TASK_NAME;
    }

    @Override
    public String getTaskDescription() {
        return TASK_DESCRIPTION;
    }

    static class BasicResultEntry extends MemoryAnalysisResultEntry {

        protected BasicResultEntry() {
            super(null, null);
        }

        @Override
        public String getWarningMessage() {
            return TASK_WARNING;
        }

        @Override
        public String getCategory() {
            return TEST_CATEGORY;
        }
    }
}
