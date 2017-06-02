# Performance tools native

Native (C++) binaries and dependencies used by preformance tools insfrastructure.

**All sections below expect you to start in `.../tools/base/profiler/native`**

## To compile all the android and host binaries:
```
../gradlew compileAndroid(Debug|Release)
```
## To compile only the host binaries:
```
../gradlew compileHost(Debug|Release)
```
## To run the host unit tests:
```
../gradlew checkHost(Debug|Release)
```
## To run the lint checks:
```
../gradlew lintHost(Debug|Release)
```
## To compile for a specific ABI (arm64-v8a for example):
```
../gradlew compileAndroidArm64V8a(Debug|Release)
```

