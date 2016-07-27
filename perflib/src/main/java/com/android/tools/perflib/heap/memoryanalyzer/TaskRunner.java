package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.analyzer.AnalysisReport.Listener;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.analyzer.Capture;
import com.android.tools.perflib.analyzer.CaptureGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a set of {@link MemoryAnalyzerTask}s and returns the results.
 */
final class TaskRunner {

    /**
     * Blocks until the given tasks are run through a {@link MemoryAnalyzer}.
     *
     * <p>All result entries will be aggregated into one list and returned.
     *
     * @param captureGroup the perflib {@link CaptureGroup} to run the tasks on.
     * @return list of results, or null if the report was cancelled or the tasks interrupted.
     */
    static List<AnalysisResultEntry<?>> runTasks(
            @NonNull Set<MemoryAnalyzerTask> tasks, @NonNull Set<Listener> listeners,
            @NonNull CaptureGroup captureGroup) {

        final List<AnalysisResultEntry<?>> generatedEntries = new ArrayList<>();

        final ExecutorService executorService = Executors.newSingleThreadExecutor();

        // Setup listeners - user supplied listeners from this.listeners, plus our own custom listener.
        final Set<Listener> listenerSet = new HashSet<>();
        listenerSet.addAll(listeners);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean cancelledOrInterrupted = new AtomicBoolean(false);
        listenerSet.add(
                new Listener() {
                    @Override
                    public void onResultsAdded(List<AnalysisResultEntry<?>> entries) {
                        generatedEntries.addAll(entries);
                    }

                    @Override
                    public void onAnalysisComplete() {
                        latch.countDown();
                    }

                    @Override
                    public void onAnalysisCancelled() {
                        cancelledOrInterrupted.set(true);
                        latch.countDown();
                    }
                });

        MemoryAnalyzer memoryAnalyzer = new MemoryAnalyzer();
        memoryAnalyzer.analyze(captureGroup, listenerSet, tasks, executorService, executorService);

        // Block until complete.
        try {
            latch.await();
        } catch (InterruptedException e) {
            cancelledOrInterrupted.set(true);
        }

        executorService.shutdownNow();

        if (!cancelledOrInterrupted.get()) {
            return generatedEntries;
        } else {
            return null;
        }
    }

    static List<AnalysisResultEntry<?>> runTasks(
            @NonNull Set<MemoryAnalyzerTask> tasks, @NonNull CaptureGroup captureGroup) {
        return runTasks(tasks, Collections.emptySet(), captureGroup);
    }

    static List<AnalysisResultEntry<?>> runTasks(@NonNull Set<MemoryAnalyzerTask> tasks,
            @NonNull Capture... captures) {
        CaptureGroup captureGroup = new CaptureGroup();
        for (Capture capture : captures) {
            captureGroup.addCapture(capture);
        }
        return runTasks(tasks, Collections.emptySet(), captureGroup);
    }
}
