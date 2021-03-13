# Apply Changes / Apply Code Changes / LiveLiteral codebase

## Native Debugging (Symbolizing crash-dump)

We build all our binaries (installer, app-server, agent) stripped. Which
mean there are no symbols and no line numbers. As a result, crash-dumps are
 not helpful.


```
 *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
 Build fingerprint: 'google/sargo/sargo:10/QP1A.190711.019/5790879:user/release-keys'
 Revision: 'MP1.0'
 ABI: 'arm64'
 Timestamp: 2021-03-09 14:34:38-0800
 pid: 8561, tid: 8561, name: installer  >>> /data/local/tmp/.studio/bin/installer <<<
 uid: 2000
 signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
 Cause: null pointer dereference
     x0  0000007ff5a81210  x1  0000007ff5a81368  x2  0000007ff5a812b0  x3  0000000000000001
     x4  0000000000000000  x5  00000075fe01a036  x6  000000006970692f  x7  000000002f736469
     x8  0000000000000000  x9  000000572d8e8cca  x10 0000000000000007  x11 00000075c0000000
     x12 0000000000000020  x13 00000000000004d0  x14 0000000000000040  x15 0000000000000008
     x16 00000075fe73a8f0  x17 00000075fe72c8c0  x18 00000000000b0000  x19 000000572d8055c8
     x20 0000007ff5a81368  x21 0000007ff5a81388  x22 0000000000000003  x23 0000000000000000
     x24 0000000000000000  x25 0000000000000000  x26 0000000000000000  x27 0000000000000000
     x28 0000000000000000  x29 0000007ff5a81160
     sp  0000007ff5a81120  lr  000000572d803a78  pc  000000572d803a84
 
 backtrace:
     NOTE: Function names and BuildId information is missing for some frames due
     NOTE: to unreadable libraries. For unwinds of apps, only shared libraries
     NOTE: found under the lib/ directory are readable.
       #00 pc 0000000000014a84  /data/local/tmp/.studio/bin/installer
       #01 pc 0000000000015c5c  /data/local/tmp/.studio/bin/installer
       #02 pc 0000000000016624  /data/local/tmp/.studio/bin/installer
       #03 pc 000000000007d798  /apex/com.android.runtime/lib64/bionic/libc.so (__libc_init+108)
```

To build binaries with the necessary .debug_info and .debug_line section full populated,
you need to add `-g` to the compiler flag in `//tools/base/bazel/android.bzl`.

```
ANDROID_COPTS = select_android(
    [
        "-g",
    ],
    [],
)
```

Make sure debug info don't be stripped in our BUILD files.

```
//tools/base/deploy/installer/BUILD
//tools/base/deploy/agent/native/BUILD
```

Change the "stripped" dependencies to normal:
```
    binary = select({
        "//tools/base/bazel:windows": ":installer.stripped.exe",
        # "//conditions:default": ":installer.stripped",
        "//conditions:default": ":installer",
    }),
```

You also need to prevent intermediate static libraries from being stripped. This is easier done
via `//tools/adt/idea/android/build.xml` (add `--strip=never` after the target).

```
 <exec executable="${bazel}" dir="${workspace}" failonerror="true">
            <arg value="build" />
            <arg value="//tools/adt/idea/android:artifacts" />
            <arg value="--strip=never" />
            <arg value="${bazel_config}" unless:blank="${bazel_config}"/>
        </exec>
```

Build and double check you have all debug_info and debug_line sections in the binary.
```
$NDK/readelf -S $AS_BASE/bazel-bin/tools/base/deploy/installer/android-installer/arm64-v8a/installer | grep debug
  [26] .debug_aranges    PROGBITS         0000000000000000  00152cac
  [27] .debug_info       PROGBITS         0000000000000000  0015bf4c
  [28] .debug_abbrev     PROGBITS         0000000000000000  0032442c
  [29] .debug_line       PROGBITS         0000000000000000  003415ee
  [30] .debug_str        PROGBITS         0000000000000000  0039157d
  [31] .debug_loc        PROGBITS         0000000000000000  003d413d
  [32] .debug_ranges     PROGBITS         0000000000000000  0059e7fe
```

Double check you have the line numbers for a file you know made it in the binary (e.g: main).

```
./toolchains/x86-4.9/prebuilt/darwin-x86_64/i686-linux-android/bin/readelf --debug-dump=decodedline $BASE/master-dev/bazel-bin/tools/base/deploy/installer/android-installer/arm64-v8a/installer | grep tools/base/deploy/installer/main.cc
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
tools/base/deploy/installer/main.cc:
```

Makes sure you are using at least Android 11 since previous version [had issues](https://github.com/android/ndk/issues/1366).

Inject the PC found in the crash-dump into addr2line.

```
llvm-addr2line -Cfe $BASE/bazel-bin/tools/base/deploy/installer/android-installer/arm64-v8a/installer 0x0000000000015c5c
```

Et voila:
```
tools/base/deploy/installer/main.cc:153
```


## Native Debugging (Enabling Asan)

Switch to ndk 20 by editing `//tools/base/bazel/bazel` (change env['ANDROID_NDK_HOME']).

Enable ASAN in `//tools/base/bazel/android.bzl`:

```
ANDROID_COPTS = select_android(
    [
        "-fsanitize=address",
        "-fno-omit-frame-pointer",
    ],
    [],
)

ANDROID_LINKOPTS = select_android(
    [
        "-fsanitize=address",
    ],
    [],
)
```

To symbolize ASAN report, follow the steps in section "Symbolizing crash-dump".
Note that you cannot use ASAN for ART jvmti agent yet because of b/182005971.