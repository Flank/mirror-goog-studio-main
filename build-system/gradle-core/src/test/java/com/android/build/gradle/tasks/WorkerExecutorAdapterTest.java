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

package com.android.build.gradle.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

import com.android.ide.common.workers.WorkerExecutorFacade;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link WorkerExecutorAdapter} class. */
public class WorkerExecutorAdapterTest {

    @Mock WorkerExecutor workerExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSingleDelegation() {

        WorkerExecutorFacade<Parameter> adapter =
                new WorkerExecutorAdapter<>(workerExecutor, WorkAction.class);
        Parameter params = new Parameter("one", "two");
        adapter.submit(params);
        adapter.await();
        ArgumentCaptor<Action<? super WorkerConfiguration>> workItemCaptor =
                ArgumentCaptor.forClass(Action.class);
        Mockito.verify(workerExecutor).submit(eq(WorkAction.class), workItemCaptor.capture());
        Mockito.verify(workerExecutor).await();
        Mockito.verifyNoMoreInteractions(workerExecutor);

        ArgumentCaptor<Object> parameterCaptor = ArgumentCaptor.forClass(Object.class);
        WorkerConfiguration workerConfiguration = Mockito.mock(WorkerConfiguration.class);
        workItemCaptor.getValue().execute(workerConfiguration);

        Mockito.verify(workerConfiguration).setIsolationMode(eq(IsolationMode.NONE));
        Mockito.verify(workerConfiguration).setParams(parameterCaptor.capture());
        Mockito.verifyNoMoreInteractions(workerConfiguration);
        assertThat(parameterCaptor.getValue()).isEqualTo(params);
    }

    @Test
    public void testMultipleDelegation() {

        WorkerExecutorFacade<Parameter> adapter =
                new WorkerExecutorAdapter<>(workerExecutor, WorkAction.class);
        List<Parameter> parametersList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Parameter params = new Parameter("+" + i, "-" + i);
            adapter.submit(params);
            parametersList.add(params);
        }
        adapter.await();
        ArgumentCaptor<Action<? super WorkerConfiguration>> workItemCaptor =
                ArgumentCaptor.forClass(Action.class);
        Mockito.verify(workerExecutor, times(5))
                .submit(eq(WorkAction.class), workItemCaptor.capture());
        Mockito.verify(workerExecutor).await();
        Mockito.verifyNoMoreInteractions(workerExecutor);

        int index = 0;
        for (Action<? super WorkerConfiguration> action : workItemCaptor.getAllValues()) {
            WorkerConfiguration workerConfiguration = Mockito.mock(WorkerConfiguration.class);
            ArgumentCaptor<Object> parameterCaptor = ArgumentCaptor.forClass(Object.class);
            action.execute(workerConfiguration);

            Mockito.verify(workerConfiguration).setIsolationMode(eq(IsolationMode.NONE));
            Mockito.verify(workerConfiguration).setParams(parameterCaptor.capture());
            Mockito.verifyNoMoreInteractions(workerConfiguration);
            assertThat(parameterCaptor.getValue()).isEqualTo(parametersList.get(index++));
        }
    }

    private static class WorkAction implements Runnable {

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
