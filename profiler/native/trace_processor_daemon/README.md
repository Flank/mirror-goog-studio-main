# TraceProcessor Daemon

The TraceProcessor Daemon (*TPD*) wraps around the TraceProcessor C++ library
from Perfetto and exposes a set of gRPC APIs to load and query trace files
produced by Perfetto.


## TPD Release Process

After merging your commits into HEAD, Studio postsubmit bots for windows, linux
and mac will copy the optimized binary into their outputs.

Copy those binaries into `.../prebuilts/tools/common/trace-processor-daemon/`,
dropping each binary into the corresponding platform sub-directory.

Run `chmod +x` for the Mac and Linux ones.

Update `.../prebuilts/tools/common/trace-processor-daemon/version` with the
build id of the postsubmit build which you grabbed the binaries from.

## Local e2e Testing

For local e2e testing (running the IDE built from HEAD while also observing the
changes made locally to TPD), build the daemon with:
```
bazel build --config=release //tools/base/profiler/native/trace_processor_daemon
```

It will tell you where the binary was generated. Copy it into
```
.../prebuilts/tools/common/trace-processor-daemon/[local_platform]
```

You might need to `chmod +x` if on Linux or Mac.
Remember to `git restore` the binary file when you're done.

