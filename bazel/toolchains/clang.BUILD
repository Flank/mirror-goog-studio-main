package(default_visibility = ["//visibility:public"])

load("@//tools/base/bazel/toolchains:cc_toolchain_config.bzl", "CLANG_LATEST", "cc_toolchain_config")

clang_latest_darwin = CLANG_LATEST["darwin"]
clang_latest_linux = CLANG_LATEST["k8"]
clang_latest_windows = CLANG_LATEST["x64_windows"]

cc_toolchain_suite(
    name = "toolchain",
    toolchains = {
        "k8|compiler": ":cc-compiler-k8",
        "k8": ":cc-compiler-k8",
        "darwin|compiler": ":cc-compiler-darwin",
        "darwin": ":cc-compiler-darwin",
        "x64_windows|clang-cl": ":cc-compiler-x64_windows-clang-cl",
        "x64_windows": ":cc-compiler-x64_windows-clang-cl",
    },
)

filegroup(
    name = "toolchain_deps",
    srcs =
        glob([clang_latest + "/**/*" for clang_latest in CLANG_LATEST.values()] + ["studio-extra/**/*"]) + ["@//tools/base/bazel/toolchains:extra_files"],
)

filegroup(
    name = "empty",
    srcs = [],
)

cc_toolchain(
    name = "cc-compiler-k8",
    all_files = ":toolchain_deps",
    ar_files = ":toolchain_deps",
    as_files = ":toolchain_deps",
    compiler_files = ":toolchain_deps",
    dwp_files = ":empty",
    linker_files = ":toolchain_deps",
    objcopy_files = ":empty",
    strip_files = ":empty",
    supports_param_files = 1,
    toolchain_config = ":local_linux",
    toolchain_identifier = "local_linux",
)

cc_toolchain(
    name = "cc-compiler-darwin",
    all_files = ":toolchain_deps",
    ar_files = ":toolchain_deps",
    as_files = ":toolchain_deps",
    compiler_files = ":toolchain_deps",
    dwp_files = ":toolchain_deps",
    linker_files = ":toolchain_deps",
    objcopy_files = ":toolchain_deps",
    strip_files = ":toolchain_deps",
    supports_param_files = 0,
    toolchain_config = ":local_darwin",
    toolchain_identifier = "local_darwin",
)

cc_toolchain(
    name = "cc-compiler-x64_windows-clang-cl",
    all_files = ":toolchain_deps",
    ar_files = ":toolchain_deps",
    as_files = ":empty",
    compiler_files = ":toolchain_deps",
    dwp_files = ":toolchain_deps",
    linker_files = ":toolchain_deps",
    objcopy_files = ":empty",
    strip_files = ":empty",
    supports_param_files = 1,
    toolchain_config = ":local_windows",
    toolchain_identifier = "local_windows",
)

cc_toolchain_config(
    name = "local_linux",
    abi_libc_version = "local",
    abi_version = "local",
    compile_flags = [
        "-U_FORTIFY_SOURCE",
        "-fstack-protector",
        "-Wall",
        "-Wthread-safety",
        "-Wself-assign",
        "-fcolor-diagnostics",
        "-fno-omit-frame-pointer",
    ],
    compiler = "compiler",
    coverage_compile_flags = ["--coverage"],
    coverage_link_flags = ["--coverage"],
    cpu = "k8",
    cxx_builtin_include_directories = [
        "/usr/local/include",
        clang_latest_linux + "/lib64/clang/9.0.4/include",
        "/usr/include/x86_64-linux-gnu",
        "/usr/include",
        "/usr/include/c++/8.0.1",
        "/usr/include/x86_64-linux-gnu/c++/8.0.1",
        "/usr/include/c++/8.0.1/backward",
    ],
    cxx_flags = ["-std=c++17"],
    dbg_compile_flags = ["-g"],
    host_system_name = "local",
    link_flags = [
        "-Wl,-no-as-needed",
        "-Wl,-z,relro,-z,now",
        "-lstdc++",
        "-lm",
    ],
    link_libs = [],
    opt_compile_flags = [
        "-g0",
        "-O2",
        "-D_FORTIFY_SOURCE=1",
        "-DNDEBUG",
        "-ffunction-sections",
        "-fdata-sections",
    ],
    opt_link_flags = ["-Wl,--gc-sections"],
    target_libc = "local",
    target_system_name = "local",
    tool_paths = {
        "ar": clang_latest_linux + "/bin/llvm-ar",
        "ld": clang_latest_linux + "/bin/ld.lld",
        "cpp": clang_latest_linux + "/bin/clang++",
        "gcc": clang_latest_linux + "/bin/clang",
        "dwp": "None",
        "gcov": "None",
        "nm": clang_latest_linux + "/bin/llvm-nm",
        "objcopy": clang_latest_linux + "/bin/llvm-objcopy",
        "objdump": clang_latest_linux + "/bin/llvm-objdump",
        "strip": clang_latest_linux + "/bin/llvm-strip",
    },
    toolchain_identifier = "local",
    unfiltered_compile_flags = [
        "-no-canonical-prefixes",
        "-Wno-builtin-macro-redefined",
        "-D__DATE__=\"redacted\"",
        "-D__TIMESTAMP__=\"redacted\"",
        "-D__TIME__=\"redacted\"",
    ],
)

cc_toolchain_config(
    name = "local_darwin",
    toolchain_identifier = "local_darwin",
    host_system_name = "local",
    target_system_name = "local",
    cpu = "darwin",
    target_libc = "macosx",
    compiler = "compiler",
    abi_version = "local",
    abi_libc_version = "local",
    cxx_builtin_include_directories = [
        clang_latest_darwin + "/include/c++/v1",
        clang_latest_darwin + "/lib64/clang/9.0.4/include",
        "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/",
        "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks",
        "/usr/include",
    ],
    tool_paths = {
        "ar": "studio-extra/wrapped_ar.darwin",
        "compat-ld": "/usr/bin/ld/",
        "cpp": clang_latest_darwin + "/bin/clang++",
        "dwp": "/bin/false",
        "gcc": clang_latest_darwin + "/bin/clang",
        "gcov": clang_latest_darwin + "/bin/llvm-cov",
        "ld": "/usr/bin/ld",
        "nm": clang_latest_darwin + "/bin/llvm-nm",
        "objcopy": clang_latest_darwin + "/bin/llvm-objcopy",
        "objdump": clang_latest_darwin + "/bin/llvm-objdump",
        "strip": clang_latest_darwin + "/bin/llvm-strip",
    },
    compile_flags = [
        "-D_FORTIFY_SOURCE=1",
        "-fstack-protector",
        "-fcolor-diagnostics",
        "-Wall",
        "-Wthread-safety",
        "-Wself-assign",
        "-fno-omit-frame-pointer",
    ],
    dbg_compile_flags = ["-g"],
    opt_compile_flags = [
        "-g0",
        "-O2",
        "-DNDEBUG",
        "-ffunction-sections",
        "-fdata-sections",
    ],
    cxx_flags = ["-std=c++17"],
    link_flags = [
        "-lc++",
        "-framework",
        "CoreFoundation",
        # Needed since we're still using /usr/bin/ld
        "-B",
        "/usr/bin/",
        "-headerpad_max_install_names",
        "-no-canonical-prefixes",
        "-undefined",
        "dynamic_lookup",
    ],
    link_libs = [],  # ?
    opt_link_flags = [],  # ?
    unfiltered_compile_flags = [
        "-no-canonical-prefixes",
        "-Wno-builtin-macro-redefined",
        "-D__DATE__=\"redacted\"",
        "-D__TIMESTAMP__=\"redacted\"",
        "-D__TIME__=\"redacted\"",
        "-idirafter/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/",
        "-F/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks",
    ],
    coverage_compile_flags = [],  # ?
    coverage_link_flags = [],  # ?
)

cc_toolchain_config(
    name = "local_windows",
    abi_libc_version = "local",
    abi_version = "local",
    compiler = "clang-cl",
    cpu = "x64_windows",
    cxx_builtin_include_directories = [
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Tools\\MSVC\\14.26.28801\\include",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.18362.0\\ucrt",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.18362.0\\um",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.18362.0\\shared",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.18362.0\\winrt",
        "C:\\botcode\\w",
    ],
    cxx_flags = ["/std:c++17"],
    link_flags = ["/MACHINE:X64"],
    compile_flags = [
        "/DCOMPILER_MSVC",
        "/DNOMINMAX",
        "/D_WIN32_WINNT=0x0601",
        "/D_CRT_SECURE_NO_DEPRECATE",
        "/D_CRT_SECURE_NO_WARNINGS",
        "/bigobj",
        "/Zm500",
        "/EHsc",
        "/wd4351",
        "/wd4291",
        "/wd4250",
        "/wd4996",
    ],
    host_system_name = "local",
    link_libs = [
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Tools\\MSVC\\14.26.28801\\lib\\x64",
        "C:\\Program Files (x86)\\Windows Kits\\10\\lib\\10.0.18362.0\\ucrt\\x64",
        "C:\\Program Files (x86)\\Windows Kits\\10\\lib\\10.0.18362.0\\um\\x64",
    ],
    tmp_path = "C:\\Users\\ContainerAdministrator\\AppData\\Local\\Temp",
    target_libc = "msvcrt",
    target_system_name = "local",
    tool_paths = {
        "ar": clang_latest_windows + "/bin/llvm-lib.exe",
        "cpp": clang_latest_windows + "/bin/clang-cl.exe",
        "gcc": clang_latest_windows + "/bin/clang-cl.exe",
        "gcov": "wrapper/bin/msvc_nop.bat",
        # TODO: remove wrapper when buildbot on windows supports symlinks
        "ld": clang_latest_windows + "/bin/link.exe",
        "nm": "wrapper/bin/msvc_nop.bat",
        "objcopy": "wrapper/bin/msvc_nop.bat",
        "objdump": "wrapper/bin/msvc_nop.bat",
        "strip": "wrapper/bin/msvc_nop.bat",
        "ml": "c:/Program Files (x86)/Microsoft Visual Studio/2019/BuildTools/VC/Tools/MSVC/14.26.28801/bin/HostX64/x64/ml64.exe",
    },
    toolchain_identifier = "local_windows",
)

toolchain(
    name = "cc-toolchain-darwin",
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:osx",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:osx",
    ],
    toolchain = ":cc-compiler-darwin",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
)

toolchain(
    name = "cc-toolchain-x64_linux",
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    toolchain = ":cc-compiler-k8",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
)

toolchain(
    name = "cc-toolchain-x64_windows-clang-cl",
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    toolchain = ":cc-compiler-x64_windows-clang-cl",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
)
