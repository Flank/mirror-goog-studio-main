package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.Instance;

/**
 * Represents an object capable of printing documents.
 */
public interface Printer {

    void addHeading(int level, @NonNull String content);

    void addParagraph(@NonNull String content);

    /**
     * Start a table which will have the given column headings. If no column headings are supplied,
     * the table will simply start with the first row of data. This should be followed by calls to
     * addRow. Be sure to end the table with endTable().
     */
    void startTable(@NonNull String... columnHeadings);

    void addRow(@NonNull String... values);

    void endTable();

    void addImage(@NonNull Instance instance);

    /**
     * Turn an {@link Instance} into a string and return it.
     *
     * <p>Some printers may apply special formatting when printing an Instance object; for example,
     * the {@link HtmlPrinter} puts {@code instance.toString()} in bold. If you do not want your
     * printer to apply any special format, simply return {@code instance.toString()}.
     */
    String formatInstance(@NonNull Instance instance);
}
