The libraries in here are symlinks. In order to generate the libraries backing this
app, run

```
$ bazel build //tools/base/app-inspection/demo/livestore/library:livestore
$ bazel build //tools/base/app-inspection/demo/livestore/protocol:livestore-protocol
```
