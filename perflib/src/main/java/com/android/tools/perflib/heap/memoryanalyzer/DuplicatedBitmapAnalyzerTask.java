package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task for detecting duplicate bitmaps.
 */
public final class DuplicatedBitmapAnalyzerTask extends MemoryAnalyzerTask {

    @Override
    protected List<AnalysisResultEntry<?>> analyze(Configuration configuration, Snapshot snapshot) {

        ClassObj bitmapClass = snapshot.findClass("android.graphics.Bitmap");
        if (bitmapClass == null) {
            return Collections.emptyList();
        }

        Map<ArrayInstance, Instance> byteArrayToBitmapMap = new HashMap<>();
        Set<ArrayInstance> byteArrays = new HashSet<>();

        List<Instance> reachableInstances = new ArrayList<>();
        configuration.mHeaps.stream().forEach(heap ->
                bitmapClass.getHeapInstances(heap.getId()).stream()
                        .filter(instance -> instance.getDistanceToGcRoot() != Integer.MAX_VALUE)
                        .forEach(reachableInstances::add));
        reachableInstances.forEach(instance -> byteArrayToBitmapMap
                .put((ArrayInstance) ((ClassInstance) instance).getValues().stream()
                        .filter(fieldValue -> fieldValue.getField().getName()
                                .equals("mBuffer"))
                        .findFirst().get().getValue(), instance));
        byteArrays.addAll(byteArrayToBitmapMap.keySet());

        if (byteArrays.size() <= 1) {
            return Collections.emptyList();
        }

        List<AnalysisResultEntry<?>> results = new ArrayList<>();

        List<Set<ArrayInstance>> commonPrefixSets = new ArrayList<>();
        List<Set<ArrayInstance>> reducedPrefixSets = new ArrayList<>();
        commonPrefixSets.add(byteArrays);

        // Cache the values since instance.getValues() recreates the array on every invocation.
        Map<ArrayInstance, Object[]> cachedValues = new HashMap<>();
        cachedValues.clear();
        for (ArrayInstance instance : byteArrays) {
            cachedValues.put(instance, instance.getValues());
        }

        int columnIndex = 0;
        while (!commonPrefixSets.isEmpty()) {
            for (Set<ArrayInstance> commonPrefixArrays : commonPrefixSets) {
                Map<Object, Set<ArrayInstance>> entryClassifier = new HashMap<>(
                        commonPrefixArrays.size());

                for (ArrayInstance arrayInstance : commonPrefixArrays) {
                    Object element = cachedValues.get(arrayInstance)[columnIndex];
                    if (entryClassifier.containsKey(element)) {
                        entryClassifier.get(element).add(arrayInstance);
                    } else {
                        Set<ArrayInstance> instanceSet = new HashSet<>();
                        instanceSet.add(arrayInstance);
                        entryClassifier.put(element, instanceSet);
                    }
                }

                for (Set<ArrayInstance> branch : entryClassifier.values()) {
                    if (branch.size() <= 1) {
                        // Unique branch, ignore it and it won't be counted towards duplication.
                        continue;
                    }

                    Set<ArrayInstance> terminatedArrays = new HashSet<>();

                    // Move all ArrayInstance that we have hit the end of to the candidate result list.
                    for (ArrayInstance instance : branch) {
                        if (instance.getLength() == columnIndex + 1) {
                            terminatedArrays.add(instance);
                        }
                    }
                    branch.removeAll(terminatedArrays);

                    // Exact duplicated arrays found.
                    if (terminatedArrays.size() > 1) {
                        int byteArraySize = -1;
                        ArrayList<Instance> duplicateBitmaps = new ArrayList<>();
                        for (ArrayInstance terminatedArray : terminatedArrays) {
                            duplicateBitmaps.add(byteArrayToBitmapMap.get(terminatedArray));
                            byteArraySize = terminatedArray.getLength();
                        }
                        results.add(
                                new DuplicatedBitmapEntry(new ArrayList<>(duplicateBitmaps),
                                        byteArraySize));
                    }

                    // If there are ArrayInstances that have identical prefixes and haven't hit the
                    // end, add it back for the next iteration.
                    if (branch.size() > 1) {
                        reducedPrefixSets.add(branch);
                    }
                }
            }

            commonPrefixSets.clear();
            commonPrefixSets.addAll(reducedPrefixSets);
            reducedPrefixSets.clear();
            columnIndex++;
        }

        return results;
    }

    @Override
    public String getTaskName() {
        return "Duplicated Bitmaps";
    }

    @Override
    public String getTaskDescription() {
        return "Detects duplicated bitmaps in the application.";
    }

    /**
     * MemoryAnalysisResultEntry for DuplicatedBitmap task.
     */
    public static final class DuplicatedBitmapEntry extends MemoryAnalysisResultEntry {

        // The size of the byte array that is duplicated.
        private final int mByteArraySize;

        private DuplicatedBitmapEntry(List<Instance> duplicates, int byteArraySize) {
            super("Duplicated Bitmap", duplicates);
            this.mByteArraySize = byteArraySize;
        }

        @Override
        public String getWarningMessage() {
            return String.format(
                    "%d instances: \"%s\"",
                    mOffender.getOffenders().size(), mOffender.getOffendingDescription());
        }

        @Override
        public String getCategory() {
            return "Duplicated Bitmaps";
        }

        public int getByteArraySize() {
            return mByteArraySize;
        }
    }
}
