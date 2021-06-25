Various tests need various versions of Kotlin. Each BUILD file under this
directory corresponds to one of those Kotlin versions. The BUILD files contain
targets for each Kotlin artifact (kotlin-stdlib, kotlin-gradle-plugin, etc.).

New Kotlin versions can be installed with the `generate.sh` script.
Example usage:
```
./generate.sh 1_5_0 1.5.0
```
This will generate a file `1_5_0/BUILD` containing maven targets for Kotlin
version 1.5.0. It will also download any missing maven artifacts into prebuilts.
Tests can then add the desired subset of these targets to
their `maven_repo`. Transitive dependencies will be included
automatically---there is no need to list them manually. Example:
```
maven_repo(
    name = "test_deps",
    artifacts = [
        "//tools/base/third_party/kotlin/1_5_0:org.jetbrains.kotlin_kotlin-gradle-plugin",
    ],
```
