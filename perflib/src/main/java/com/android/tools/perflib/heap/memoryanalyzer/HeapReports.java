package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.Snapshot;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class which uses perflib to analyze Android heap dumps and creates {@link Report}s with
 * the results.
 */
public final class HeapReports {

    private HeapReports() {
    }

    /**
     * Runs a {@link MemoryAnalyzerTask} on a {@link Snapshot} and returns a {@link DefaultReport}
     * with the results.
     *
     * <p>The user is responsible for setting up the Snapshot as they need, for example by calling
     * computeDominators() or resolveClasses() on the Snapshot. It is recommended to at least call
     * computeDominators(), as most tasks will depend on the data generated in this method.
     *
     * @param task     the task which should be run and whose results should be put in the report
     * @param snapshot the heap dump to run on.
     * @return a {@link DefaultReport} with the results of the task.
     */
    public static DefaultReport generateReport(@NonNull MemoryAnalyzerTask task,
            @NonNull Snapshot snapshot) {
        DefaultReport report = new DefaultReport(task);
        generateReport(report, task, snapshot);
        return report;
    }

    /**
     * Runs a {@link MemoryAnalyzerTask} on a {@link Snapshot} and pass data to a custom {@link
     * Report}.
     *
     * <p>The user is responsible for setting up the Snapshot as they need, for example by calling
     * computeDominators() or resolveClasses() on the Snapshot. It is recommended to at least call
     * computeDominators(), as most tasks will depend on the data generated in this method.
     *
     * <p>It is the user's responsibility to ensure that the report matches the task; otherwise,
     * this method's behavior is undefined. For example, if you pass a DuplicatedStringsReport, you
     * need to pass a DuplicatedStringsAnalyzerTask.
     *
     * @param report   an instance of the custom Report, which will be generated and ready to print
     *                 when the method returns.
     * @param task     the task which should be run and whose results should be put in the report.
     * @param snapshot the heap dump to run on.
     */
    public static void generateReport(@NonNull Report report, @NonNull MemoryAnalyzerTask task,
            @NonNull Snapshot snapshot) {
        Set<MemoryAnalyzerTask> tasks = new HashSet<>();
        tasks.add(task);
        List<AnalysisResultEntry<?>> results = TaskRunner.runTasks(tasks, snapshot);
        report.generate(results);
    }
}
