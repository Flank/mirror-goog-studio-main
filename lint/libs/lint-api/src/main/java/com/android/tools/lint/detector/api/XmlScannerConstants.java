package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

public interface XmlScannerConstants {
    /**
     * Special marker collection returned by {@link XmlScanner#getApplicableElements()} or {@link
     * XmlScanner#getApplicableAttributes()} to indicate that the check should be invoked on all
     * elements or all attributes
     */
    @NonNull List<String> ALL = new ArrayList<>(0); // NOT Collections.EMPTY!
    // We want to distinguish this from just an *empty* list returned by the caller!
}
