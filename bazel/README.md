# How to Bazel

This directory contains the core files to run studio-master-dev tests using
bazel.

## Running bazel

Bazel has the concept of a _workspace_: the root of all your source files. For
us, it is where we `repo init` our source tree. For google3 vets, this is the
"google3" directory. In this document we assume the current directory to be the
workspace, but note that bazel can be run from anywhere in the tree.

Bazel is checked-in at `tools/base/bazel/bazel`. To make things easy you might
want to link it like this (assuming `~/bin` is in `$PATH`)

```shell
ln -s <workspace>/tools/base/bazel ~/bin/bazel
```

Then no matter where you are in the workspace, bazel will find the right
platform-specific binary and run it.

## Running all the tests

*** note
Warning: On a Mac, append `--genrule_strategy=standalone --spawn_strategy=standalone`
***

The command to run all the bazel tests run by the PSQ is:

```shell
bazel test $(<tools/base/bazel/targets)
```

The test output is typically present in `bazel-testlogs/pkg/target/test.xml` (or `test.log`)

To run all the tests found in `tools/base`:

```shell
bazel test //tools/base/...
```

To run all the tests in the IntelliJ Android plugin:

```
bazel test //tools/adt/idea/android/...
```

> Note: The file `tools/base/bazel/targets` contains the up-to-date list of test targets.

To build Studio without running the tests:

```
bazel build //tools/adt/idea/...
```

To run a single test:

```
bazel test //tools/adt/idea/android/... --test_filter=AndroidLayoutDomTest
```

To debug a single test, which will open remote debugging:

```
bazel test //tools/adt/idea/android/... --test_filter=AndroidLayoutDomTest --java_debug
```

## Useful Bazel options

 * `--nocache_test_results` may be required if you are trying to re-run a test without changing
   anything.
 * `--test_filter=<TestName>` to run a specific test

## BUILD files

BUILD files define a package with a set build and test rules. In order to
support Android Studio we created a new kind of rule, that matches an IntelliJ
module: the `iml_module` rule.

> Note that we modify these BUILD files manually, so whenever you make a change
> to an `.iml` file, its corresponding BUILD file will have to be changed.

### iml_module

```
iml_module(name, srcs, test_srcs, exclude, resources, test_resources, deps, test_runtime_deps,
visibility, exports,javacopts, test_data, test_timeout, test_class, test_shard_count, tags)
```

This rule will generate the targets:

*   _name_: The production library for this module.
*   _name_\_testlib: The test library for this module.
*   _name_\_tests: The test target to run this module's tests.

#### Example

```
iml_module(
    name = "android",
    srcs = ["src/main/java"],
    resources = ["src/main/resources"],
    test_data = glob(["testData/**"]),
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    deps = [
        "//a/module/only/needed/in/tests:name[module, test]",
        "//a/standard/java/dependency:dep",
        "//path/to/libs:junit-4.12[test]",
        "//path/to/module:name[module]",
    ],
)
```

Attribute        | Description
---------------- | -----------
`name`           | The name of the rule (usually matching Studio's module name).
`srcs`           | A list of directories containing the sources. .java, .groovy, .kotlin and .form files are supported.
`resources`      | A list directories with the production resources.
`deps`           | A tag-enhanced list of dependencies, of the form `//label[tag1,tag2,...]`. Supported tags are: `module`, for iml_module dependencies, and `test` for test only dependencies.
`test_srcs`      | A list of directories with the test sources.
`test_resources` | A list of directories with the test resources.
`test_data`      | A list of files needed to run the test.

> A major difference with actual iml modules is that in bazel we must specify
> the files needed to run the tests. These files are known as _runfiles_ and are
> specified via the `test_data` attribute. This is essential to determining
> which test targets need to be run when an arbitrary file has changed.

## Circular Dependencies

_Just don't_. IntelliJ has support for circular dependencies of modules, but we
do not use it in our code base.

## Additional tools

There are several other tools in this package that can be used to manage
dependencies in prebuilts.

### third\_party\_build\_generator

Used to generate `//tools/base/third_party/BUILD`. Computes effective versions
of all necessary dependencies and creates a `java_library` rule for each one of
them. It will also download missing jars into
`//prebuilts/tools/common/m2/repository`.

Invoked by running:

```
bazel run //tools/base/bazel:third_party_build_generator
```

The tool looks for names and versions of libraries in `//tools/buildSrc/base/dependencies.properties`.
The same file is read by our Gradle scripts, to keep the set of dependencies consistent between the
two.

### add\_dependency

Can be used to download one or more Maven artifacts (JARs, AARs or APKs) into
prebuilts, including all transitive dependencies.

Invoked by running:

```
bazel run //tools/base/bazel:add_dependency com.example:foo:1.0 com.example:android-lib:aar:1.0
```

You can also use it to download protoc binaries, like this:

```
bazel run //tools/base/bazel:add_dependency com.google.protobuf:protoc:exe:linux-x86_64:3.0.0
```

The tool by default uses Maven Central, JCenter and the Google Maven
repository. You can add more (like a staging repository for libraries to be
pushed to maven.google.com) using a flag:

```
bazel run //tools/base/bazel:add_dependency -- --repo=https://example.com/m2 com.example:foo:1.0
```

### java\_import\_generator

Creates a BUILD file for every POM file in the prebuilts maven repo. Both of the
binaries above do the same, but this can be useful if prebuilts was modified
using Gradle's `cloneArtifacts` tasks or manually.

Invoked by running:

```
bazel run //tools/base/bazel:java_import_generator
```
