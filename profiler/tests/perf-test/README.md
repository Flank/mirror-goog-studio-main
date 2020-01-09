# Profiler Integration Tests

The tests in this module run against a fake profiler app (see
`//tools/base/profiler/tests/test-app`), verifying that the APIs which
communicate across host and device work as expected.

These tests are built on top of the transport test framework.
You may wish to see also: `//tools/base/transport/test-framework/README.md`

## Running the tests

This section includes common recipes for running tests in this module.

**Note that these tests are only supported on Linux, and they will either fail
to run or no-op on Mac / Windows.**

### Everything

```
$ bazel test //tools/base/profiler/tests/perf-test/...
```

### Individual tests

List all tests:

```
$ bazel query 'tests(//tools/base/profiler/tests/perf-test/...)'
```

Run a test:

```
$ bazel test //tools/base/profiler/tests/perf-test:NetworkTest
$ bazel test //tools/base/profiler/tests/perf-test:MemoryTest
```

Useful, common args:

```
$ bazel test --test_output=all --nocache_test_results \
  //tools/base/profiler/tests/...
```

