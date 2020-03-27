To run these tests for iml_to_build, run
```
bazel test //tools/base/bazel:iml_to_build_tests
```

To update `BUILD.bazel.test` (the golden file) run
```
bazel run //tools/base/bazel:iml_to_build -- \
    --workspace $SRC/tools/base/bazel/test/iml_to_bazel \
    --project_path .
```
