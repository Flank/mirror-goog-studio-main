/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for the {@link Workers.WorkerExecutorAdapter} class. */
public class WorkerExecutorAdapterTest {

    @Mock WorkerExecutor workerExecutor;

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        Workers.INSTANCE.initFromProject(
                new ProjectOptions(
                        ImmutableMap.of(
                                BooleanOption.ENABLE_GRADLE_WORKERS.getPropertyName(),
                                Boolean.TRUE)),
                ForkJoinPool.commonPool());
    }

    @After
    public void tearDown() {
        Workers.INSTANCE.initFromProject(
                new ProjectOptions(
                        ImmutableMap.of(
                                BooleanOption.ENABLE_GRADLE_WORKERS.getPropertyName(),
                                Boolean.FALSE)),
                ForkJoinPool.commonPool());
    }

    @Test
    public void testSingleDelegation() {
        WorkerExecutorFacade adapter = Workers.INSTANCE.getWorker(":test", workerExecutor);
        Parameter params = new Parameter("one", "two");
        adapter.submit(WorkAction.class, params);
        adapter.await();
        ArgumentCaptor<Action<? super WorkerConfiguration>> workItemCaptor =
                ArgumentCaptor.forClass(Action.class);
        Mockito.verify(workerExecutor)
                .submit(eq(Workers.ActionFacade.class), workItemCaptor.capture());
        Mockito.verify(workerExecutor).await();
        Mockito.verifyNoMoreInteractions(workerExecutor);

        WorkerConfiguration workerConfiguration = Mockito.mock(WorkerConfiguration.class);
        workItemCaptor.getValue().execute(workerConfiguration);

        Mockito.verify(workerConfiguration).setIsolationMode(IsolationMode.NONE);
        ArgumentCaptor<Workers.ActionParameters> actionParametersArgumentCaptor =
                ArgumentCaptor.forClass(Workers.ActionParameters.class);
        Mockito.verify(workerConfiguration).params(actionParametersArgumentCaptor.capture());
        Workers.ActionParameters actionParameters = actionParametersArgumentCaptor.getValue();
        Truth.assertThat(actionParameters.getDelegateParameters()).isEqualTo(params);
        Truth.assertThat(actionParameters.getDelegateAction()).isEqualTo(WorkAction.class);
        Truth.assertThat(actionParameters.getTaskOwner()).isEqualTo(":test");
        Mockito.verifyNoMoreInteractions(workerConfiguration);
    }

    @Test
    public void testMultipleDelegation() {

        WorkerExecutorFacade adapter = Workers.INSTANCE.getWorker(":test", workerExecutor);
        List<Parameter> parametersList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Parameter params = new Parameter("+" + i, "-" + i);
            adapter.submit(WorkAction.class, params);
            parametersList.add(params);
        }
        adapter.await();
        ArgumentCaptor<Action<? super WorkerConfiguration>> workItemCaptor =
                ArgumentCaptor.forClass(Action.class);
        Mockito.verify(workerExecutor, times(5))
                .submit(eq(Workers.ActionFacade.class), workItemCaptor.capture());
        Mockito.verify(workerExecutor).await();
        Mockito.verifyNoMoreInteractions(workerExecutor);

        int index = 0;
        for (Action<? super WorkerConfiguration> action : workItemCaptor.getAllValues()) {
            WorkerConfiguration workerConfiguration = Mockito.mock(WorkerConfiguration.class);
            action.execute(workerConfiguration);

            Mockito.verify(workerConfiguration).setIsolationMode(eq(IsolationMode.NONE));
            ArgumentCaptor<Workers.ActionParameters> actionParametersArgumentCaptor =
                    ArgumentCaptor.forClass(Workers.ActionParameters.class);
            Mockito.verify(workerConfiguration).params(actionParametersArgumentCaptor.capture());
            Workers.ActionParameters actionParameters = actionParametersArgumentCaptor.getValue();
            Truth.assertThat(actionParameters.getDelegateParameters())
                    .isEqualTo(parametersList.get(index++));
            Truth.assertThat(actionParameters.getDelegateAction()).isEqualTo(WorkAction.class);
            Truth.assertThat(actionParameters.getTaskOwner()).isEqualTo(":test");
            Mockito.verifyNoMoreInteractions(workerConfiguration);
        }
    }

    private static class WorkAction implements Runnable {

        @Inject
        WorkAction(Parameter parameter) {}

        @Override
        public void run() {}
    }

    private static class Parameter implements Serializable {
        final String subParamOne;
        final String subParamTwo;

        private Parameter(String subParamOne, String subParamTwo) {
            this.subParamOne = subParamOne;
            this.subParamTwo = subParamTwo;
        }
    }
}
