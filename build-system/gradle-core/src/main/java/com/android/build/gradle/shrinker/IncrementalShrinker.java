/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.shrinker;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.ClassReader;

/** Code for incremental shrinking. */
public class IncrementalShrinker<T> extends AbstractShrinker<T> {

    /**
     * Exception thrown when the incremental shrinker detects incompatible changes and requests a
     * full run instead.
     */
    public static class IncrementalRunImpossibleException extends RuntimeException {
        IncrementalRunImpossibleException(String message) {
            super(message);
        }

        IncrementalRunImpossibleException(Throwable cause) {
            super("Failed to load incremental state.", cause);
        }
    }

    public IncrementalShrinker(
            WaitableExecutor<Void> executor,
            ShrinkerGraph<T> graph,
            ShrinkerLogger shrinkerLogger) {
        super(graph, executor, shrinkerLogger);
    }

    /**
     * Perform incremental shrinking, in the supported cases (where only code in pre-existing
     * methods has been modified).
     *
     * <p>The general idea is this: for every method in modified classes, remove all outgoing "code
     * reference" edges, add them again based on the current code and then set the counters again
     * (traverse the graph) using the new set of edges.
     *
     * <p>The counters are re-calculated every time from scratch (starting from known entry points
     * from the config file) to avoid cycles being left in the output.
     *
     * @throws IncrementalRunImpossibleException If incremental shrinking is impossible and a full
     *     run should be done instead.
     */
    public void incrementalRun(
            @NonNull Iterable<TransformInput> inputs, @NonNull TransformOutputProvider output)
            throws IOException, IncrementalRunImpossibleException {
        final Set<T> classesToWrite = Sets.newConcurrentHashSet();
        final Set<File> classFilesToDelete = Sets.newConcurrentHashSet();
        final Set<PostProcessingData.UnresolvedReference<T>> unresolvedReferences =
                Sets.newConcurrentHashSet();

        Stopwatch stopwatch = Stopwatch.createStarted();
        SetMultimap<T, String> oldState = resetState();
        logTime("resetState()", stopwatch);

        processInputs(inputs, classesToWrite, unresolvedReferences);
        logTime("processInputs", stopwatch);

        finishGraph(unresolvedReferences);
        logTime("finish graph", stopwatch);

        setCounters(CounterSet.SHRINK);
        logTime("set counters", stopwatch);

        chooseClassesToWrite(inputs, output, classesToWrite, classFilesToDelete, oldState);
        logTime("choose classes", stopwatch);

        updateClassFiles(classesToWrite, classFilesToDelete, inputs, output);
        logTime("update class files", stopwatch);

        mGraph.saveState();
        logTime("save state", stopwatch);
    }

    /**
     * Decides which classes need to be updated on disk and which need to be deleted. It puts
     * appropriate entries in the lists passed as arguments.
     */
    private void chooseClassesToWrite(
            @NonNull Iterable<TransformInput> inputs,
            @NonNull TransformOutputProvider output,
            @NonNull Collection<T> classesToWrite,
            @NonNull Collection<File> classFilesToDelete,
            @NonNull SetMultimap<T, String> oldState) {
        for (T klass : mGraph.getReachableClasses(CounterSet.SHRINK)) {
            if (!oldState.containsKey(klass)) {
                classesToWrite.add(klass);
            } else {
                Set<String> newMembers =
                        mGraph.getReachableMembersLocalNames(klass, CounterSet.SHRINK);
                Set<String> oldMembers = oldState.get(klass);

                // Reverse of the trick above, where we store one artificial member for empty
                // classes.
                if (oldMembers.size() == 1) {
                    oldMembers.remove(mGraph.getClassName(klass));
                }

                if (!newMembers.equals(oldMembers)) {
                    classesToWrite.add(klass);
                }
            }

            oldState.removeAll(klass);
        }

        // All keys that remained in oldState should be deleted.
        for (T klass : oldState.keySet()) {
            File sourceFile = mGraph.getSourceFile(klass);
            checkState(sourceFile != null, "One of the inputs has no source file.");

            Optional<File> outputFile = chooseOutputFile(klass, sourceFile, inputs, output);
            if (!outputFile.isPresent()) {
                throw new IllegalStateException(
                        "Can't determine path of " + mGraph.getClassName(klass));
            }
            classFilesToDelete.add(outputFile.get());
        }
    }

    /**
     * Saves all reachable classes and members in a {@link SetMultimap} and clears all counters, so
     * that the graph can be traversed again, using the new edges.
     *
     * <p>Returns a multimap that contains names of all reachable members for every reachable class.
     */
    @NonNull
    private SetMultimap<T, String> resetState() {
        SetMultimap<T, String> oldState = HashMultimap.create();

        for (T klass : mGraph.getReachableClasses(CounterSet.SHRINK)) {
            Set<String> reachableMembers =
                    mGraph.getReachableMembersLocalNames(klass, CounterSet.SHRINK);
            for (String member : reachableMembers) {
                oldState.put(klass, member);
            }

            // Make sure the key is in the map.
            if (reachableMembers.isEmpty()) {
                oldState.put(klass, mGraph.getClassName(klass));
            }
        }

        mGraph.clearCounters(mExecutor);
        waitForAllTasks();
        return oldState;
    }

    private void finishGraph(
            @NonNull Iterable<PostProcessingData.UnresolvedReference<T>> unresolvedReferences) {
        resolveReferences(unresolvedReferences);
        waitForAllTasks();
    }

    private void processInputs(
            @NonNull Iterable<TransformInput> inputs,
            @NonNull final Collection<T> classesToWrite,
            @NonNull
                    final Collection<PostProcessingData.UnresolvedReference<T>>
                            unresolvedReferences)
            throws IncrementalRunImpossibleException {
        for (final TransformInput input : inputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                switch (jarInput.getStatus()) {
                    case ADDED:
                    case REMOVED:
                    case CHANGED:
                        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
                        throw new IncrementalRunImpossibleException(
                                String.format(
                                        "Input jar %s has been %s.",
                                        jarInput.getFile(),
                                        jarInput.getStatus().name().toLowerCase()));
                    case NOTCHANGED:
                        break;
                }
            }

            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                for (final Map.Entry<File, Status> changedFile :
                        directoryInput.getChangedFiles().entrySet()) {
                    mExecutor.execute(
                            () -> {
                                switch (changedFile.getValue()) {
                                    case ADDED:
                                        throw new IncrementalRunImpossibleException(
                                                String.format(
                                                        "File %s added.", changedFile.getKey()));
                                    case REMOVED:
                                        throw new IncrementalRunImpossibleException(
                                                String.format(
                                                        "File %s removed.", changedFile.getKey()));
                                    case CHANGED:
                                        processChangedClassFile(
                                                changedFile.getKey(),
                                                unresolvedReferences,
                                                classesToWrite);
                                        break;
                                }
                                return null;
                            });
                }
            }
        }
        waitForAllTasks();
    }

    /**
     * Handles a changed class file by removing old code references (graph edges) and adding
     * up-to-date edges, according to the current state of the class.
     *
     * <p>This only works on {@link DependencyType#REQUIRED_CODE_REFERENCE} edges, which are only
     * ever created from method containing the opcode to target member. The first pass is equivalent
     * to removing all code from the method, the second to adding "current" opcodes to it.
     *
     * @throws IncrementalRunImpossibleException If current members of the class are not the same as
     *     they used to be. This means that edges of other types need to be updated, and we don't
     *     handle this incrementally. It also means that -keep rules would need to be re-applied,
     *     which is something we also don't do incrementally.
     */
    private void processChangedClassFile(
            @NonNull File file,
            @NonNull Collection<PostProcessingData.UnresolvedReference<T>> unresolvedReferences,
            @NonNull Collection<T> classesToWrite)
            throws IncrementalRunImpossibleException {
        try {
            ClassReader classReader = new ClassReader(Files.toByteArray(file));
            IncrementalRunVisitor<T> visitor =
                    new IncrementalRunVisitor<>(mGraph, classesToWrite, unresolvedReferences);

            DependencyRemoverVisitor<T> remover = new DependencyRemoverVisitor<>(mGraph, visitor);

            classReader.accept(remover, 0);
        } catch (IncrementalRunImpossibleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process " + file.getAbsolutePath(), e);
        }
    }

    @Override
    protected void waitForAllTasks() {
        try {
            super.waitForAllTasks();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IncrementalRunImpossibleException) {
                throw (IncrementalRunImpossibleException) e.getCause();
            } else {
                throw e;
            }
        }
    }
}
