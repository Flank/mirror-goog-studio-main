/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.ide.common.workers;

import java.io.Serializable;

/**
 * Some classes do not have access to the Gradle APIs like classes located in the builder module.
 * Yet those classes could potentially have a need for a facility similar to Gradle's WorkerExecutor
 * to submit work items for remove or parallel processing.
 *
 * <p>This interface implementation can be used by Task or other higher level implementation classes
 * to provide this facility.
 *
 * @param <T> T is the parameter type that will be passed to the {@link Runnable} implementing the
 *     work item. There can be only one parameter that should encapsulate all data necessary to run
 *     the work action.
 *     <p>High level interaction is as follow :
 *     <li>Task creates a WorkerExecutorFacade object that encapsulates an Executor style facility.
 *         This facade instance is passed to the classes with no access to the Gradle APIs.
 *     <li>Classes create a new Runnable subclass that use an instance of {@param T} as a work
 *         action configuration. Such {@link Runnable} cannot be passed to the Gradle WorkerExecutor
 *         directly as it relies on @Inject to set the parameter value.
 *     <li>Classes create an instance of {@param T} for each work actions and submit it with the
 *         {@link #submit(Serializable)} API.
 *     <li>Facade object should create a WorkItem that use {@param T} instances to configure the
 *         work action. <lib>Facade object should also create a {@link Runnable} action that gets
 *         its parameter of type {@param T} injected with @Inject and create an instance of the
 *         Runnable subclass created in step 2. </lib>
 *     <li>These new actions {@link Runnable#run()} should call back the {@link Runnable} instance
 *         create in step 2 {@link Runnable#run()}
 */
public interface WorkerExecutorFacade<T extends Serializable> {

    /**
     * Submit a new work action to be performed.
     *
     * @param parameter the parameter instance to pass to the action.
     */
    void submit(T parameter);

    /** Wait for all submitted work actions completion. */
    void await();
}
