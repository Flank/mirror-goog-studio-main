# Performance Tracking in Studio Tests

Metric is a helper class in logging performance data while running
Android Studio tests on our post-submit queue. The data is then auto-uploaded
to a dashboard system for visualization and further regression analysis.

## Logging

As an example, to track the total runtime of a particular test, include
the following code in a test:

```
// “My Test” represents a data series within a single dashboard
Metric myTestMetric = new Metric("My Test");
// “Total Time” refers to the dashboard the data will be uploaded to.
Benchmark benchmark = new Benchmark("Total Time");

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