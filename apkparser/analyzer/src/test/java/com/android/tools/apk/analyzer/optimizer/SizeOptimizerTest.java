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

package com.android.tools.apk.analyzer.optimizer;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

public class SizeOptimizerTest extends TestCase {

    public void testGatheredSuggestions() {
        SizeOptimizer optimizer = new SizeOptimizer(null, null, null);
        List<Suggestion> suggestions = new ArrayList();
        suggestions.add(new TestSuggestion());
        TestAnalyzer analyzer = new TestAnalyzer();
        analyzer.setSuggestions(suggestions);
        optimizer.setAnalyzers(new ArrayList<>(Arrays.asList(analyzer)));

        List<Suggestion> results = optimizer.analyze();

        assertThat(results.size()).isEqualTo(suggestions.size());
        Suggestion suggestion = results.get(0);
        assertThat(suggestion).isInstanceOf(TestSuggestion.class);
    }

    public void testNoSuggestions() {
        SizeOptimizer optimizer = new SizeOptimizer(null, null, null);
        TestAnalyzer analyzer = new TestAnalyzer();
        optimizer.setAnalyzers(new ArrayList<>(Arrays.asList(analyzer)));

        List<Suggestion> results = optimizer.analyze();

        assertThat(results.isEmpty()).isTrue();
    }

    private class TestAnalyzer implements Analyzer {

        private List<Suggestion> suggestions;

        @Override
        public List<Suggestion> analyze() {
            return suggestions;
        }

        public void setSuggestions(List<Suggestion> suggestions) {
            this.suggestions = suggestions;
        }
    }

    private class TestSuggestion implements Suggestion {

        @Override
        public long getSizeSavingsEstimate() {
            return 0;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public QuickFix getQuickFix() {
            return null;
        }
    }
}
