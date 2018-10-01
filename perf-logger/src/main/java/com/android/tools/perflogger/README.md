# Performance Tracking in Studio Tests

Metric is a helper class in logging performance data while running
Android Studio tests on our post-submit queue. The data is then auto-uploaded
to a dashboard system for visualization and further regression analysis.

## Logging

As an example, to track the total runtime of a particular test, include
the following code in a test:

```
// “My Test” represents a data series within a single dashboard
Metric myTestMetric = new Metric("My_Test");
// “Total Time” refers to the dashboard the data will be uploaded to.
Benchmark benchmark = new Benchmark.Builder("Total Time").build();

// Your test code goes here...

// Log a sample point for “My Test” onto “Total Run Time”
// timeMs is milliseconds relative to Unix Epoch.
myTestMetric.addSamples(benchmark, new MetricSample(timeMs, testRunTimeMs));
// Saves the data.
myTestMetric.commit();
```

Please see the documentation and references of the Metric class for more
usage examples.

## Dashboards

On every post-submit where the test(s) are run, our buildbot uploads the data to
an internal dashboard system located at [go/adt-perfgate](go/adt-perfgate).

If the dashboard(s) and data series do not exist already, they will be auto-
generated based on the String parameters provided to Benchmark (dashboard name)
and Metric (data series name).

The uploaded data are tagged with the buildbot recipe, build # and the name of
the machine where the test(s) were run on, so you can navigate back to the build
page to see the related commits or filter data by a particular recipe or machine.

## Analyzers

Perfgate supports regression detection via statistical analysis. See 
[go/perfgate-analyzers](http://goto.google.com/perfgate-analyzers) for more
information about Analyzers. 

With this package, you can specify Analyzers which will be run when the data is
uploaded to Perfgate. If any of the Analyzers fail, a notification email will be
sent to notify interested parties of the regression. Note that this step occurs
after the buildbot has finished the post-submit run, so regressions do not
surface in the build results UI at all. 

For example:
```
Metric myTestMetric = new Metric("My_Test");
Benchmark benchmark = new Benchmark.Builder("Total Time").build();
myAnalyzer = new WindowDeviationAnalyzer.Builder()
        .setMetricAggregate(Analyzer.MetricAggregate.MEDIAN)
        .setRunInfoQueryLimit(50)
        .addMedianTolerance(new WindowDeviationAnalyzer.MedianToleranceParams.Builder()
                .setConstTerm(10.0)
                .build())
        .build();
myTestMetric.setAnalyzers(benchmark, ImmutableList.of(myAnalyzer));

// Not shown: add data samples to myTestMetric and call commit()
```

This package currently only includes support for configuring Window Deviation
Analyzers. If multiple ToleranceParams are added to a WindowDeviationAnalyzer,
then all specified Tolerances must be exceeded for the Analyzer to fail.

## Other Benchmark Data
Each Benchmark can be accompanied by a plaintext description of the Benchmark's
purpose, which is shown on the Aggregate Chart page, accessible via 
[go/adt-perfgate](go/adt-perfgate). This data must be written to disk via the
`PerfData` class, but is otherwise sent to Perfgate in the same manner as the 
rest of the performance data.

```
Benchmark benchmark = new Benchmark.Builder("My Benchmark")
        .setDescription("Test benchmark. Do not upvote.")
        .build();      

// Not shown: create Metrics, add samples, and call commit() on each

PerfData myPerfData = new PerfData();
myPerfData.addBenchmark(benchmark);
myPerfData.commit();
```

This package also supports attaching key/value-structured metadata to
Benchmarks, but such data is not displayed in any of the provided Perfgate
interfaces. It is only accessible via programmatic interaction with Perfgate
Storage, and should only be used if you plan to build additional tooling
(such as a custom dashboard).

