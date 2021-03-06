/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.profile

import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.concurrent.GuardedBy

/**
 * Book keeping object for a Gradle [org.gradle.api.Task] execution.
 *
 * Information contained in each instance will be use to upload our performance data per task
 * once optional workers profiling information has been gathered.
 */
open class TaskProfilingRecord
/**
 * Construct a new task profiling record with the [GradleBuildProfileSpan] and decorations like
 * project path and variant name.
 */(
    private val resourceManager: AnalyticsResourceManager,
    span: GradleBuildProfileSpan.Builder,
    private val taskPath: String,
    internal val projectPath: String,
    internal val variant: String?
) {

    val spanBuilder: GradleBuildProfileSpan.Builder = span
    private val workerRecordList: MutableMap<String, WorkerProfilingRecord> = mutableMapOf()
    private var startTime = Instant.MIN
    private var endTime = Instant.MIN
    internal val status = AtomicReference(Status.RUNNING)

    // taskSpan is modified by addSpan() and passed to the writer in writeTaskSpan, we need to make
    // sure we are not modifying them while they are being used by the recordWriter.
    @get:GuardedBy("this")
    val taskSpans = mutableListOf<GradleBuildProfileSpan>()

    /**
     * Possible run state of the Task we are book keeping for.
     */
    internal enum class Status {
        /**
         * Task is in running state
         */
        RUNNING,
        /**
         * Task invoked [com.android.ide.common.workers.WorkerExecutorFacade.await] method and has
         * blocked waiting for spawned workers to be completed before resuming its activities.
         * Since we do not intercept when the task resumes, it is unclear if the task is still
         * blocked waiting or running.
         */
        AWAIT,

        /**
         * Gradle invoked the [org.gradle.tooling.events.OperationCompletionListener.onFinish]
         * method when the associated task is finished.
         */
        FINISHED,

        /**
         * Task is finished and span record was written.
         */
        SPAN_CLOSED
    }

    fun setTaskWaiting() {
        status.set(Status.AWAIT)
    }

    open fun addWorker(key: String) {
        addWorker(key, GradleBuildProfileSpan.ExecutionType.THREAD_EXECUTION)
    }

    @Synchronized
    open fun addWorker(key: String, type: GradleBuildProfileSpan.ExecutionType) {
        val workerRecord =
            WorkerProfilingRecord(
                taskPath,
                type,
                clock.instant()
            )
        workerRecordList[key] = workerRecord
    }

    @Synchronized
    open fun get(key: String): WorkerProfilingRecord? = workerRecordList[key]

    @Synchronized
    fun allWorkersFinished(): Boolean {
        return workerRecordList.isEmpty() || workerRecordList.values.stream()
            .allMatch(WorkerProfilingRecord::isFinished)
    }

    /**
     * Notification that a worker item has finished executing.
     *
     * Add a new span for the worker item and possibly close the task span if all spawned
     * worker item have finished executing.
     */
    @Synchronized
    fun workerFinished(workerRecord: WorkerProfilingRecord) {

        // create the span for the worker item itself
        val workerSpan = GradleBuildProfileSpan.newBuilder()
            .setId(resourceManager.allocateRecordId())
            .setParentId(spanBuilder.id)

        workerRecord.fillSpanRecord(workerSpan)

        resourceManager.writeRecord(projectPath, variant, workerSpan, listOf())
    }

    @Synchronized
    fun writeTaskSpan() {
        if (status.get() == Status.SPAN_CLOSED) return

        status.set(Status.SPAN_CLOSED)

        spanBuilder.startTimeInMs = startTime.toEpochMilli()
        spanBuilder.type = GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION
        spanBuilder.durationInMs = duration().toMillis()

        resourceManager.writeRecord(
            projectPath,
            variant,
            spanBuilder,
            taskSpans
        )
    }

    fun minimumWaitTime(): Duration = workerRecordList.values.asSequence()
        .map { it.waitTime() }
        .minOrNull() ?: Duration.ZERO

    /**
     * Calculate the task duration time.
     */
    fun duration(): Duration = Duration.between(startTime, endTime)

    @Synchronized
    fun addSpan(builder: GradleBuildProfileSpan.Builder) {
        builder.parentId = spanBuilder.id
        taskSpans.add(builder.build())
    }

    fun setTaskStartTime(startTime: Long) {
        this.startTime = Instant.ofEpochMilli(startTime)
    }

    fun setTaskEndTime(endTime: Long) {
        status.set(Status.FINISHED)
        this.endTime = Instant.ofEpochMilli(endTime)
    }

    companion object {
        /**
         * Clock used to measure tasks and workers timings.
         */
        @VisibleForTesting
        var clock: Clock = Clock.systemDefaultZone()
    }
}
