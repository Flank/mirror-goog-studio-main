package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;

import java.util.List;

/**
 * Formats a set of {@link AnalysisResultEntry}s in a human-readable way.
 *
 * <p>In their {@link #generate} method, Reports process a list of results of a perflib {@link
 * MemoryAnalyzerTask}. In their {@link #print} method, Reports visualize the results through a
 * printer interface. <p>{@link DefaultReport} is provided as a basic Report implementation.
 */
public interface Report {

    /**
     * Take {@link MemoryAnalyzerTask} results and process them - e.g. sort the results.
     */
    void generate(@NonNull List<AnalysisResultEntry<?>> data);

    /**
     * Print report to a {@link Printer}.
     *
     * <p>Please read the Printer interface documentation to see what methods are available when
     * overriding print().
     */
    void print(@NonNull Printer printer);

}
