"""
Configuration for the native toolchains used by Studio
This is a heavily modified version of the default toolchain configurations
(as referenced in toplevel.WORKSPACE) used by bazel to support compiling
with checked-in clang on all platforms.
"""

load(
    "@bazel_tools//tools/cpp:cc_toolchain_config_lib.bzl",
    "action_config",
    "artifact_name_pattern",
    "env_entry",
    "env_set",
    "feature",
    "feature_set",
    "flag_group",
    "flag_set",
    "tool",
    "tool_path",
    "variable_with_value",
    "with_feature_set",
)
load("@bazel_tools//tools/build_defs/cc:action_names.bzl", "ACTION_NAMES")

CLANG_LATEST = {
    "darwin": "host/darwin-x86/clang-r353983e",
    "x64_windows": "host/windows-x86/clang-r353983d",
    "k8": "host/linux-x86/clang-r365631b",
}

all_compile_actions = [
    ACTION_NAMES.c_compile,
    ACTION_NAMES.cpp_compile,
    ACTION_NAMES.linkstamp_compile,
    ACTION_NAMES.assemble,
    ACTION_NAMES.preprocess_assemble,
    ACTION_NAMES.cpp_header_parsing,
    ACTION_NAMES.cpp_module_compile,
    ACTION_NAMES.cpp_module_codegen,
    ACTION_NAMES.clif_match,
    ACTION_NAMES.lto_backend,
]

all_cpp_compile_actions = [
    ACTION_NAMES.cpp_compile,
    ACTION_NAMES.linkstamp_compile,
    ACTION_NAMES.cpp_header_parsing,
    ACTION_NAMES.cpp_module_compile,
    ACTION_NAMES.cpp_module_codegen,
    ACTION_NAMES.clif_match,
]

preprocessor_compile_actions = [
    ACTION_NAMES.c_compile,
    ACTION_NAMES.cpp_compile,
    ACTION_NAMES.linkstamp_compile,
    ACTION_NAMES.preprocess_assemble,
    ACTION_NAMES.cpp_header_parsing,
    ACTION_NAMES.cpp_module_compile,
    ACTION_NAMES.clif_match,
]

codegen_compile_actions = [
    ACTION_NAMES.c_compile,
    ACTION_NAMES.cpp_compile,
    ACTION_NAMES.linkstamp_compile,
    ACTION_NAMES.assemble,
    ACTION_NAMES.preprocess_assemble,
    ACTION_NAMES.cpp_module_codegen,
    ACTION_NAMES.lto_backend,
]

all_link_actions = [
    ACTION_NAMES.cpp_link_executable,
    ACTION_NAMES.cpp_link_dynamic_library,
    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
]

def _impl(ctx):
    tool_paths = [
        tool_path(name = name, path = path)
        for name, path in ctx.attr.tool_paths.items()
    ]
    objcopy_embed_data_action = action_config(
        action_name = "objcopy_embed_data",
        enabled = True,
        tools = [tool(path = "host/darwin-x86/{}/bin/llvm-objcopy".format(CLANG_LATEST["darwin"]))],
    )

    action_configs = []
    if ctx.attr.cpu == "darwin":
        action_configs = [objcopy_embed_data_action]
    elif ctx.attr.cpu == "x64_windows":
        cpp_link_nodeps_dynamic_library_action = action_config(
            action_name = ACTION_NAMES.cpp_link_nodeps_dynamic_library,
            implies = [
                "nologo",
                "shared_flag",
                "linkstamps",
                "output_execpath_flags",
                "input_param_flags",
                "user_link_flags",
                "default_link_flags",
                "linker_subsystem_flag",
                "linker_param_file",
                "msvc_env",
                "no_stripping",
                "has_configured_linker_path",
                "def_file",
            ],
            tools = [tool(path = ctx.attr.tool_paths["ld"])],
        )

        cpp_link_static_library_action = action_config(
            action_name = ACTION_NAMES.cpp_link_static_library,
            implies = [
                "nologo",
                "archiver_flags",
                "input_param_flags",
                "linker_param_file",
                "msvc_env",
            ],
            tools = [tool(path = ctx.attr.tool_paths["ar"])],
        )

        assemble_action = action_config(
            action_name = ACTION_NAMES.assemble,
            implies = [
                "compiler_input_flags",
                "compiler_output_flags",
                "nologo",
                "msvc_env",
                "sysroot",
            ],
            tools = [tool(path = ctx.attr.tool_paths["ml"])],
        )

        preprocess_assemble_action = action_config(
            action_name = ACTION_NAMES.preprocess_assemble,
            implies = [
                "compiler_input_flags",
                "compiler_output_flags",
                "nologo",
                "msvc_env",
                "sysroot",
            ],
            tools = [tool(path = ctx.attr.tool_paths["ml"])],
        )

        c_compile_action = action_config(
            action_name = ACTION_NAMES.c_compile,
            implies = [
                "compiler_input_flags",
                "compiler_output_flags",
                "default_compile_flags",
                "nologo",
                "msvc_env",
                "parse_showincludes",
                "user_compile_flags",
                "sysroot",
                "unfiltered_compile_flags",
            ],
            tools = [tool(path = ctx.attr.tool_paths["gcc"])],
        )
        cpp_compile_action = action_config(
            action_name = ACTION_NAMES.cpp_compile,
            implies = [
                "compiler_input_flags",
                "compiler_output_flags",
                "default_compile_flags",
                "nologo",
                "msvc_env",
                "parse_showincludes",
                "user_compile_flags",
                "sysroot",
                "unfiltered_compile_flags",
            ],
            tools = [tool(path = ctx.attr.tool_paths["gcc"])],
        )

        cpp_link_executable_action = action_config(
            action_name = ACTION_NAMES.cpp_link_executable,
            implies = [
                "nologo",
                "linkstamps",
                "output_execpath_flags",
                "input_param_flags",
                "user_link_flags",
                "default_link_flags",
                "linker_subsystem_flag",
                "linker_param_file",
                "msvc_env",
                "no_stripping",
            ],
            tools = [tool(path = ctx.attr.tool_paths["ld"])],
        )

        cpp_link_dynamic_library_action = action_config(
            action_name = ACTION_NAMES.cpp_link_dynamic_library,
            implies = [
                "nologo",
                "shared_flag",
                "linkstamps",
                "output_execpath_flags",
                "input_param_flags",
                "user_link_flags",
                "default_link_flags",
                "linker_subsystem_flag",
                "linker_param_file",
                "msvc_env",
                "no_stripping",
                "has_configured_linker_path",
                "def_file",
            ],
            tools = [tool(path = ctx.attr.tool_paths["ld"])],
        )

        action_configs = [
            assemble_action,
            preprocess_assemble_action,
            c_compile_action,
            cpp_compile_action,
            cpp_link_executable_action,
            cpp_link_dynamic_library_action,
            cpp_link_nodeps_dynamic_library_action,
            cpp_link_static_library_action,
        ]

    artifact_name_patterns = []
    if ctx.attr.cpu == "x64_windows":
        artifact_name_patterns = [
            artifact_name_pattern(
                category_name = "object_file",
                prefix = "",
                extension = ".obj",
            ),
            artifact_name_pattern(
                category_name = "static_library",
                prefix = "",
                extension = ".lib",
            ),
            artifact_name_pattern(
                category_name = "alwayslink_static_library",
                prefix = "",
                extension = ".lo.lib",
            ),
            artifact_name_pattern(
                category_name = "executable",
                prefix = "",
                extension = ".exe",
            ),
            artifact_name_pattern(
                category_name = "dynamic_library",
                prefix = "",
                extension = ".dll",
            ),
            artifact_name_pattern(
                category_name = "interface_library",
                prefix = "",
                extension = ".if.lib",
            ),
        ]

    supports_pic_feature = feature(
        name = "supports_pic",
        enabled = True,
    )

    supports_start_end_lib_feature = feature(
        name = "supports_start_end_lib",
        enabled = True,
    )

    if ctx.attr.cpu == "darwin":
        macos_target_min_requirement_feature = feature(
            name = "macos_target_min_requirement",
            enabled = True,
            env_sets = [
                env_set(
                    actions = all_compile_actions +
                              all_link_actions +
                              [ACTION_NAMES.cpp_link_static_library],
                    env_entries = [env_entry(key = "MACOSX_DEPLOYMENT_TARGET", value = "10.13")],
                ),
            ],
        )
    elif ctx.attr.cpu == "x64_windows":
        msvc_link_env_feature = feature(
            name = "msvc_link_env",
            env_sets = [
                env_set(
                    actions = all_link_actions +
                              [ACTION_NAMES.cpp_link_static_library],
                    env_entries = [env_entry(key = "LIB", value = ";".join(ctx.attr.link_libs))],
                ),
            ],
        )
        msvc_compile_env_feature = feature(
            name = "msvc_compile_env",
            env_sets = [
                env_set(
                    actions = [
                        ACTION_NAMES.c_compile,
                        ACTION_NAMES.cpp_compile,
                        ACTION_NAMES.cpp_module_compile,
                        ACTION_NAMES.cpp_module_codegen,
                        ACTION_NAMES.cpp_header_parsing,
                        ACTION_NAMES.assemble,
                        ACTION_NAMES.preprocess_assemble,
                    ],
                    env_entries = [env_entry(key = "INCLUDE", value = ";".join(ctx.attr.cxx_builtin_include_directories))],
                ),
            ],
        )
        msvc_env_feature = feature(
            name = "msvc_env",
            env_sets = [
                env_set(
                    actions = [
                        ACTION_NAMES.c_compile,
                        ACTION_NAMES.cpp_compile,
                        ACTION_NAMES.cpp_module_compile,
                        ACTION_NAMES.cpp_module_codegen,
                        ACTION_NAMES.cpp_header_parsing,
                        ACTION_NAMES.assemble,
                        ACTION_NAMES.preprocess_assemble,
                        ACTION_NAMES.cpp_link_executable,
                        ACTION_NAMES.cpp_link_dynamic_library,
                        ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                        ACTION_NAMES.cpp_link_static_library,
                    ],
                    env_entries = [
                        env_entry(key = "PATH", value = ctx.attr.tmp_path),
                        env_entry(key = "TMP", value = ctx.attr.tmp_path),
                        env_entry(key = "TEMP", value = ctx.attr.tmp_path),
                    ],
                ),
            ],
            implies = ["msvc_compile_env", "msvc_link_env"],
        )

    default_compile_flags_feature = feature(
        name = "default_compile_flags",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = ([
                    flag_group(
                        flags = ctx.attr.compile_flags,
                    ),
                ] if ctx.attr.compile_flags else []),
            ),
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = ([
                    flag_group(
                        flags = ctx.attr.dbg_compile_flags,
                    ),
                ] if ctx.attr.dbg_compile_flags else []),
                with_features = [with_feature_set(features = ["dbg"])],
            ),
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = ([
                    flag_group(
                        flags = ctx.attr.opt_compile_flags,
                    ),
                ] if ctx.attr.opt_compile_flags else []),
                with_features = [with_feature_set(features = ["opt"])],
            ),
            flag_set(
                actions = [
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = ([
                    flag_group(
                        flags = ctx.attr.cxx_flags,
                    ),
                ] if ctx.attr.cxx_flags else []),
            ),
        ],
    )

    default_link_flags_feature = feature(
        name = "default_link_flags",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = ([
                    flag_group(
                        flags = ctx.attr.link_flags,
                    ),
                ] if ctx.attr.link_flags else []),
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = ([
                    flag_group(
                        flags = ctx.attr.opt_link_flags,
                    ),
                ] if ctx.attr.opt_link_flags else []),
                with_features = [with_feature_set(features = ["opt"])],
            ),
        ],
    )

    compiler_param_file_feature = feature(
        name = "compiler_param_file",
    )

    copy_dynamic_libraries_to_binary_feature = feature(
        name = "copy_dynamic_libraries_to_binary",
    )

    input_param_flags_feature = feature(
        name = "input_param_flags",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["/IMPLIB:%{interface_library_output_path}"],
                        expand_if_available = "interface_library_output_path",
                    ),
                ],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["%{libopts}"],
                        iterate_over = "libopts",
                        expand_if_available = "libopts",
                    ),
                    flag_group(
                        iterate_over = "libraries_to_link",
                        flag_groups = [
                            flag_group(
                                iterate_over = "libraries_to_link.object_files",
                                flag_groups = [flag_group(flags = ["%{libraries_to_link.object_files}"])],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file_group",
                                ),
                            ),
                            flag_group(
                                flag_groups = [flag_group(flags = ["%{libraries_to_link.name}"])],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file",
                                ),
                            ),
                            flag_group(
                                flag_groups = [flag_group(flags = ["%{libraries_to_link.name}"])],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "interface_library",
                                ),
                            ),
                            flag_group(
                                flag_groups = [
                                    flag_group(
                                        flags = ["%{libraries_to_link.name}"],
                                        expand_if_false = "libraries_to_link.is_whole_archive",
                                    ),
                                    flag_group(
                                        flags = ["/WHOLEARCHIVE:%{libraries_to_link.name}"],
                                        expand_if_true = "libraries_to_link.is_whole_archive",
                                    ),
                                ],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "static_library",
                                ),
                            ),
                        ],
                        expand_if_available = "libraries_to_link",
                    ),
                ],
            ),
        ],
    )

    opt_feature = feature(
        name = "opt",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/O2"])],
            ),
        ] if ctx.attr.cpu == "x64_windows" else [],
        implies = ["frame_pointer"] if ctx.attr.cpu == "x64_windows" else [],
    )

    fastbuild_feature = feature(
        name = "fastbuild",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/Od", "/Z7"])],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["/DEBUG", "/INCREMENTAL:NO"],
                    ),
                ],
            ),
        ],
        implies = ["generate_pdb_file"],
    )

    generate_pdb_file_feature = feature(
        name = "generate_pdb_file",
        requires = [
            feature_set(features = ["dbg"]),
            feature_set(features = ["fastbuild"]),
        ],
    )

    dynamic_link_msvcrt_no_debug_feature = feature(
        name = "dynamic_link_msvcrt_no_debug",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/MD"])],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [flag_group(flags = ["/DEFAULTLIB:msvcrt.lib"])],
            ),
        ],
        requires = [
            feature_set(features = ["fastbuild"]),
            feature_set(features = ["opt"]),
        ],
    )

    disable_assertions_feature = feature(
        name = "disable_assertions",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/DNDEBUG"])],
                with_features = [with_feature_set(features = ["opt"])],
            ),
        ],
    )

    sysroot_feature = feature(
        name = "sysroot",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.clif_match,
                    ACTION_NAMES.cpp_link_executable,
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["--sysroot=%{sysroot}"],
                        iterate_over = "sysroot",
                        expand_if_available = "sysroot",
                    ),
                ],
            ),
        ],
    )

    fdo_optimize_feature = feature(
        name = "fdo_optimize",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [
                    flag_group(
                        flags = [
                            "-fprofile-use=%{fdo_profile_path}",
                            "-fprofile-correction",
                        ],
                        expand_if_available = "fdo_profile_path",
                    ),
                ],
            ),
        ],
        provides = ["profile"],
    )

    supports_dynamic_linker_feature = feature(name = "supports_dynamic_linker", enabled = True)

    user_compile_flags_feature = feature(
        name = "user_compile_flags",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["%{user_compile_flags}"],
                        iterate_over = "user_compile_flags",
                        expand_if_available = "user_compile_flags",
                    ),
                ],
            ),
        ],
    )

    unfiltered_compile_flags_feature = feature(
        name = "unfiltered_compile_flags",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = ([
                    flag_group(
                        flags = ctx.attr.unfiltered_compile_flags,
                    ),
                ] if ctx.attr.unfiltered_compile_flags else []),
            ),
        ],
    )

    library_search_directories_feature = feature(
        name = "library_search_directories",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["-L%{library_search_directories}"],
                        iterate_over = "library_search_directories",
                        expand_if_available = "library_search_directories",
                    ),
                ],
            ),
        ],
    )

    static_libgcc_feature = feature(
        name = "static_libgcc",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_executable,
                    ACTION_NAMES.cpp_link_dynamic_library,
                ],
                flag_groups = [flag_group(flags = ["-static-libgcc"])],
                with_features = [
                    with_feature_set(features = ["static_link_cpp_runtimes"]),
                ],
            ),
        ],
    )

    pic_feature = feature(
        name = "pic",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.cpp_module_compile,
                ],
                flag_groups = [
                    flag_group(flags = ["-fPIC"], expand_if_available = "pic"),
                ],
            ),
        ],
    )

    per_object_debug_info_feature = feature(
        name = "per_object_debug_info",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_codegen,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["-gsplit-dwarf"],
                        expand_if_available = "per_object_debug_info_file",
                    ),
                ],
            ),
        ],
    )

    preprocessor_defines_feature = feature(
        name = "preprocessor_defines",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["/D%{preprocessor_defines}" if ctx.attr.cpu == "x64_windows" else "-D%{preprocessor_defines}"],
                        iterate_over = "preprocessor_defines",
                    ),
                ],
            ),
        ],
    )

    cs_fdo_optimize_feature = feature(
        name = "cs_fdo_optimize",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.lto_backend],
                flag_groups = [
                    flag_group(
                        flags = [
                            "-fprofile-use=%{fdo_profile_path}",
                            "-Xclang-only=-Wno-profile-instr-unprofiled",
                            "-Xclang-only=-Wno-profile-instr-out-of-date",
                            "-fprofile-correction",
                        ],
                        expand_if_available = "fdo_profile_path",
                    ),
                ],
            ),
        ],
        provides = ["csprofile"],
    )

    autofdo_feature = feature(
        name = "autofdo",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [
                    flag_group(
                        flags = [
                            "-fauto-profile=%{fdo_profile_path}",
                            "-fprofile-correction",
                        ],
                        expand_if_available = "fdo_profile_path",
                    ),
                ],
            ),
        ],
        provides = ["profile"],
    )

    runtime_library_search_directories_feature = feature(
        name = "runtime_library_search_directories",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        iterate_over = "runtime_library_search_directories",
                        flag_groups = [
                            flag_group(
                                flags = [
                                    "-Wl,-rpath,$EXEC_ORIGIN/%{runtime_library_search_directories}",
                                ],
                                expand_if_true = "is_cc_test",
                            ),
                            flag_group(
                                flags = [
                                    "-Wl,-rpath,$ORIGIN/%{runtime_library_search_directories}",
                                ],
                                expand_if_false = "is_cc_test",
                            ),
                        ],
                        expand_if_available =
                            "runtime_library_search_directories",
                    ),
                ],
                with_features = [
                    with_feature_set(features = ["static_link_cpp_runtimes"]),
                ],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        iterate_over = "runtime_library_search_directories",
                        flag_groups = [
                            flag_group(
                                flags = [
                                    "-Wl,-rpath,$ORIGIN/%{runtime_library_search_directories}",
                                ],
                            ),
                        ],
                        expand_if_available =
                            "runtime_library_search_directories",
                    ),
                ],
                with_features = [
                    with_feature_set(
                        not_features = ["static_link_cpp_runtimes"],
                    ),
                ],
            ),
        ],
    )

    fission_support_feature = feature(
        name = "fission_support",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["-Wl,--gdb-index"],
                        expand_if_available = "is_using_fission",
                    ),
                ],
            ),
        ],
    )

    shared_flag_feature = feature(
        name = "shared_flag",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                ],
                flag_groups = [flag_group(flags = ["/DLL" if ctx.attr.cpu == "x64_windows" else "-shared"])],
            ),
        ],
    )

    determinism_feature = feature(
        name = "determinism",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [
                    flag_group(
                        flags = [
                            "/wd4117",
                            "-D__DATE__=\"redacted\"",
                            "-D__TIMESTAMP__=\"redacted\"",
                            "-D__TIME__=\"redacted\"",
                        ] + (["-Wno-builtin-macro-redefined"] if ctx.attr.compiler == "clang-cl" else []),
                    ),
                ],
            ),
        ],
    )

    random_seed_feature = feature(
        name = "random_seed",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.cpp_module_compile,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["-frandom-seed=%{output_file}"],
                        expand_if_available = "output_file",
                    ),
                ],
            ),
        ],
    )

    includes_feature = feature(
        name = "includes",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.clif_match,
                    ACTION_NAMES.objc_compile,
                    ACTION_NAMES.objcpp_compile,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["-include", "%{includes}"],
                        iterate_over = "includes",
                        expand_if_available = "includes",
                    ),
                ],
            ),
        ],
    )

    fdo_instrument_feature = feature(
        name = "fdo_instrument",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                    ACTION_NAMES.cpp_link_executable,
                ],
                flag_groups = [
                    flag_group(
                        flags = [
                            "-fprofile-generate=%{fdo_instrument_path}",
                            "-fno-data-sections",
                        ],
                        expand_if_available = "fdo_instrument_path",
                    ),
                ],
            ),
        ],
        provides = ["profile"],
    )

    cs_fdo_instrument_feature = feature(
        name = "cs_fdo_instrument",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.lto_backend,
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                    ACTION_NAMES.cpp_link_executable,
                ],
                flag_groups = [
                    flag_group(
                        flags = [
                            "-fcs-profile-generate=%{cs_fdo_instrument_path}",
                        ],
                        expand_if_available = "cs_fdo_instrument_path",
                    ),
                ],
            ),
        ],
        provides = ["csprofile"],
    )

    include_paths_feature = feature(
        name = "include_paths",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.linkstamp_compile,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.clif_match,
                    ACTION_NAMES.objc_compile,
                    ACTION_NAMES.objcpp_compile,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["/I%{quote_include_paths}"],
                        iterate_over = "quote_include_paths",
                    ),
                    flag_group(
                        flags = ["/I%{include_paths}"],
                        iterate_over = "include_paths",
                    ),
                    flag_group(
                        flags = ["/I%{system_include_paths}"],
                        iterate_over = "system_include_paths",
                    ),
                ] if ctx.attr.cpu == "x64_windows" else [
                    flag_group(
                        flags = ["-iquote", "%{quote_include_paths}"],
                        iterate_over = "quote_include_paths",
                    ),
                    flag_group(
                        flags = ["-I%{include_paths}"],
                        iterate_over = "include_paths",
                    ),
                    flag_group(
                        flags = ["-isystem", "%{system_include_paths}"],
                        iterate_over = "system_include_paths",
                    ),
                ],
            ),
        ],
    )

    symbol_counts_feature = feature(
        name = "symbol_counts",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = [
                            "-Wl,--print-symbol-counts=%{symbol_counts_output}",
                        ],
                        expand_if_available = "symbol_counts_output",
                    ),
                ],
            ),
        ],
    )

    llvm_coverage_map_format_feature = feature(
        name = "llvm_coverage_map_format",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.objc_compile,
                    ACTION_NAMES.objcpp_compile,
                ],
                flag_groups = [
                    flag_group(
                        flags = [
                            "-fprofile-instr-generate",
                            "-fcoverage-mapping",
                        ],
                    ),
                ],
            ),
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                    ACTION_NAMES.cpp_link_executable,
                    "objc-executable",
                    "objc++-executable",
                ],
                flag_groups = [
                    flag_group(flags = ["-fprofile-instr-generate"]),
                ],
            ),
        ],
        requires = [feature_set(features = ["coverage"])],
        provides = ["profile"],
    )

    strip_debug_symbols_feature = feature(
        name = "strip_debug_symbols",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["-Wl,-S"],
                        expand_if_available = "strip_debug_symbols",
                    ),
                ],
            ),
        ],
    )

    build_interface_libraries_feature = feature(
        name = "build_interface_libraries",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                ],
                flag_groups = [
                    flag_group(
                        flags = [
                            "%{generate_interface_library}",
                            "%{interface_library_builder_path}",
                            "%{interface_library_input_path}",
                            "%{interface_library_output_path}",
                        ],
                        expand_if_available = "generate_interface_library",
                    ),
                ],
                with_features = [
                    with_feature_set(
                        features = ["supports_interface_shared_libraries"],
                    ),
                ],
            ),
        ],
    )

    supports_interface_shared_libraries_feature = feature(
        name = "supports_interface_shared_libraries",
        enabled = True,
    )

    libraries_to_link_feature = feature(
        name = "libraries_to_link",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        iterate_over = "libraries_to_link",
                        flag_groups = [
                            flag_group(
                                flags = ["-Wl,--start-lib"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file_group",
                                ),
                            ),
                            flag_group(
                                flags = ["-Wl,-whole-archive"],
                                expand_if_true =
                                    "libraries_to_link.is_whole_archive",
                            ),
                            flag_group(
                                flags = ["%{libraries_to_link.object_files}"],
                                iterate_over = "libraries_to_link.object_files",
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file_group",
                                ),
                            ),
                            flag_group(
                                flags = ["%{libraries_to_link.name}"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file",
                                ),
                            ),
                            flag_group(
                                flags = ["%{libraries_to_link.name}"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "interface_library",
                                ),
                            ),
                            flag_group(
                                flags = ["%{libraries_to_link.name}"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "static_library",
                                ),
                            ),
                            flag_group(
                                flags = ["-l%{libraries_to_link.name}"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "dynamic_library",
                                ),
                            ),
                            flag_group(
                                flags = ["-l:%{libraries_to_link.name}"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "versioned_dynamic_library",
                                ),
                            ),
                            flag_group(
                                flags = ["-Wl,-no-whole-archive"],
                                expand_if_true = "libraries_to_link.is_whole_archive",
                            ),
                            flag_group(
                                flags = ["-Wl,--end-lib"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file_group",
                                ),
                            ),
                        ],
                        expand_if_available = "libraries_to_link",
                    ),
                    flag_group(
                        flags = ["-Wl,@%{thinlto_param_file}"],
                        expand_if_true = "thinlto_param_file",
                    ),
                ],
            ),
        ],
    )

    user_link_flags_feature = feature(
        name = "user_link_flags",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["%{user_link_flags}"],
                        iterate_over = "user_link_flags",
                        expand_if_available = "user_link_flags",
                    ),
                ] + ([flag_group(flags = ctx.attr.link_libs)] if ctx.attr.link_libs and ctx.attr.cpu != "x64_windows" else []),
            ),
        ],
    )

    fdo_prefetch_hints_feature = feature(
        name = "fdo_prefetch_hints",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.lto_backend,
                ],
                flag_groups = [
                    flag_group(
                        flags = [
                            "-Xclang-only=-mllvm",
                            "-Xclang-only=-prefetch-hints-file=%{fdo_prefetch_hints_path}",
                        ],
                        expand_if_available = "fdo_prefetch_hints_path",
                    ),
                ],
            ),
        ],
    )

    linkstamps_feature = feature(
        name = "linkstamps",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["%{linkstamp_paths}"],
                        iterate_over = "linkstamp_paths",
                        expand_if_available = "linkstamp_paths",
                    ),
                ],
            ),
        ],
    )

    gcc_coverage_map_format_feature = feature(
        name = "gcc_coverage_map_format",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.objc_compile,
                    ACTION_NAMES.objcpp_compile,
                    "objc-executable",
                    "objc++-executable",
                ],
                flag_groups = [
                    flag_group(
                        flags = ["-fprofile-arcs", "-ftest-coverage"],
                        expand_if_available = "gcov_gcno_file",
                    ),
                ],
            ),
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                    ACTION_NAMES.cpp_link_executable,
                ],
                flag_groups = [flag_group(flags = ["--coverage"])],
            ),
        ],
        requires = [feature_set(features = ["coverage"])],
        provides = ["profile"],
    )

    archiver_flags_feature = feature(
        name = "archiver_flags",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.cpp_link_static_library],
                flag_groups = [
                    flag_group(
                        flags = [CLANG_LATEST["darwin"], "rcS"] if ctx.attr.cpu == "darwin" else ["/MACHINE:X64"] if ctx.attr.cpu == "x64_windows" else ["rcsD"],
                    ),
                    flag_group(
                        flags = ["/OUT:%{output_execpath}" if ctx.attr.cpu == "x64_windows" else "%{output_execpath}"],
                        expand_if_available = "output_execpath",
                    ),
                ],
            ),
            flag_set(
                actions = [ACTION_NAMES.cpp_link_static_library],
                flag_groups = [
                    flag_group(
                        iterate_over = "libraries_to_link",
                        flag_groups = [
                            flag_group(
                                flags = ["%{libraries_to_link.name}"],
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file",
                                ),
                            ),
                            flag_group(
                                flags = ["%{libraries_to_link.object_files}"],
                                iterate_over = "libraries_to_link.object_files",
                                expand_if_equal = variable_with_value(
                                    name = "libraries_to_link.type",
                                    value = "object_file_group",
                                ),
                            ),
                        ],
                        expand_if_available = "libraries_to_link",
                    ),
                ],
            ),
        ],
    )

    force_pic_flags_feature = feature(
        name = "force_pic_flags",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.cpp_link_executable],
                flag_groups = [
                    flag_group(
                        flags = ["-pie"],
                        expand_if_available = "force_pic",
                    ),
                ],
            ),
        ],
    )

    dependency_file_feature = feature(
        name = "dependency_file",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.objc_compile,
                    ACTION_NAMES.objcpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.clif_match,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["-MD", "-MF", "%{dependency_file}"],
                        expand_if_available = "dependency_file",
                    ),
                ],
            ),
        ],
    )

    dynamic_library_linker_tool_feature = feature(
        name = "dynamic_library_linker_tool",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                ],
                flag_groups = [
                    flag_group(
                        flags = [" + cppLinkDynamicLibraryToolPath + "],
                        expand_if_available = "generate_interface_library",
                    ),
                ],
                with_features = [
                    with_feature_set(
                        features = ["supports_interface_shared_libraries"],
                    ),
                ],
            ),
        ],
    )

    output_execpath_flags_feature = feature(
        name = "output_execpath_flags",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                    ACTION_NAMES.cpp_link_executable,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["/OUT:%{output_execpath}"] if ctx.attr.cpu == "x64_windows" else ["-o", "%{output_execpath}"],
                        expand_if_available = "output_execpath",
                    ),
                ],
            ),
        ],
    )

    objcopy_embed_flags_feature = feature(
        name = "objcopy_embed_flags",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = ["objcopy_embed_data"],
                flag_groups = [flag_group(flags = ["-I", "binary"])],
            ),
        ],
    )

    dbg_feature = feature(
        name = "dbg",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/Od", "/Z7"])],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["/DEBUG", "/INCREMENTAL:NO"],
                    ),
                ],
            ),
        ] if ctx.attr.cpu == "x64_windows" else [],
        implies = ["generate_pdb_file"] if ctx.attr.cpu == "x64_windows" else [],
    )

    static_link_msvcrt_feature = feature(name = "static_link_msvcrt")

    dynamic_link_msvcrt_debug_feature = feature(
        name = "dynamic_link_msvcrt_debug",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/MDd"])],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [flag_group(flags = ["/DEFAULTLIB:msvcrtd.lib"])],
            ),
        ],
        requires = [feature_set(features = ["dbg"])],
    )

    no_stripping_feature = feature(name = "no_stripping")

    # TODO: use this on mac
    lld_feature = feature(
        name = "lld",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_executable,
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["-fuse-ld=lld"] + (["-Wl,-all_load"] if ctx.attr.cpu == "darwin" else []),
                    ),
                ],
            ),
        ],
    )

    # Note that we also set --coverage for c++-link-nodeps-dynamic-library. The
    # generated code contains references to gcov symbols, and the dynamic linker
    # can't resolve them unless the library is linked against gcov.
    coverage_feature = feature(
        name = "coverage",
        provides = ["profile"],
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                ],
                flag_groups = ([
                    flag_group(flags = ctx.attr.coverage_compile_flags),
                ] if ctx.attr.coverage_compile_flags else []),
            ),
            flag_set(
                actions = [
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                    ACTION_NAMES.cpp_link_executable,
                ],
                flag_groups = ([
                    flag_group(flags = ctx.attr.coverage_link_flags),
                ] if ctx.attr.coverage_link_flags else []),
            ),
        ],
    )

    has_configured_linker_path_feature = feature(name = "has_configured_linker_path")

    linker_param_file_feature = feature(
        name = "linker_param_file",
        flag_sets = [
            flag_set(
                actions = all_link_actions +
                          [ACTION_NAMES.cpp_link_static_library],
                flag_groups = [
                    flag_group(
                        flags = ["@%{linker_param_file}"],
                        expand_if_available = "linker_param_file",
                    ),
                ],
            ),
        ],
    )

    ignore_noisy_warnings_feature = feature(
        name = "ignore_noisy_warnings",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.cpp_link_static_library],
                flag_groups = [flag_group(flags = ["/ignore:4221"])],
            ),
        ],
    )

    no_legacy_features_feature = feature(name = "no_legacy_features")

    parse_showincludes_feature = feature(
        name = "parse_showincludes",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_header_parsing,
                ],
                flag_groups = [flag_group(flags = ["/showIncludes"])],
            ),
        ],
    )

    static_link_msvcrt_no_debug_feature = feature(
        name = "static_link_msvcrt_no_debug",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/MT"])],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [flag_group(flags = ["/DEFAULTLIB:libcmt.lib"])],
            ),
        ],
        requires = [
            feature_set(features = ["fastbuild"]),
            feature_set(features = ["opt"]),
        ],
    )

    treat_warnings_as_errors_feature = feature(
        name = "treat_warnings_as_errors",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/WX"])],
            ),
        ],
    )

    windows_export_all_symbols_feature = feature(name = "windows_export_all_symbols")

    no_windows_export_all_symbols_feature = feature(name = "no_windows_export_all_symbols")

    targets_windows_feature = feature(
        name = "targets_windows",
        enabled = True,
        implies = ["copy_dynamic_libraries_to_binary"],
    )

    linker_subsystem_flag_feature = feature(
        name = "linker_subsystem_flag",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [flag_group(flags = ["/SUBSYSTEM:CONSOLE"])],
            ),
        ],
    )

    static_link_msvcrt_debug_feature = feature(
        name = "static_link_msvcrt_debug",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/MTd"])],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [flag_group(flags = ["/DEFAULTLIB:libcmtd.lib"])],
            ),
        ],
        requires = [feature_set(features = ["dbg"])],
    )

    frame_pointer_feature = feature(
        name = "frame_pointer",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/Oy-"])],
            ),
        ],
    )

    compiler_output_flags_feature = feature(
        name = "compiler_output_flags",
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.assemble],
                flag_groups = [
                    flag_group(
                        flag_groups = [
                            flag_group(
                                flags = ["/Fo%{output_file}", "/Zi"],
                                expand_if_available = "output_file",
                                expand_if_not_available = "output_assembly_file",
                            ),
                        ],
                        expand_if_not_available = "output_preprocess_file",
                    ),
                ],
            ),
            flag_set(
                actions = [
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                ],
                flag_groups = [
                    flag_group(
                        flag_groups = [
                            flag_group(
                                flags = ["/Fo%{output_file}"],
                                expand_if_not_available = "output_preprocess_file",
                            ),
                        ],
                        expand_if_available = "output_file",
                        expand_if_not_available = "output_assembly_file",
                    ),
                    flag_group(
                        flag_groups = [
                            flag_group(
                                flags = ["/Fa%{output_file}"],
                                expand_if_available = "output_assembly_file",
                            ),
                        ],
                        expand_if_available = "output_file",
                    ),
                    flag_group(
                        flag_groups = [
                            flag_group(
                                flags = ["/P", "/Fi%{output_file}"],
                                expand_if_available = "output_preprocess_file",
                            ),
                        ],
                        expand_if_available = "output_file",
                    ),
                ],
            ),
        ],
    )

    nologo_feature = feature(
        name = "nologo",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.cpp_link_executable,
                    ACTION_NAMES.cpp_link_dynamic_library,
                    ACTION_NAMES.cpp_link_nodeps_dynamic_library,
                    ACTION_NAMES.cpp_link_static_library,
                ],
                flag_groups = [flag_group(flags = ["/nologo"])],
            ),
        ],
    )

    smaller_binary_feature = feature(
        name = "smaller_binary",
        enabled = True,
        flag_sets = [
            flag_set(
                actions = [ACTION_NAMES.c_compile, ACTION_NAMES.cpp_compile],
                flag_groups = [flag_group(flags = ["/Gy", "/Gw"])],
                with_features = [with_feature_set(features = ["opt"])],
            ),
            flag_set(
                actions = all_link_actions,
                flag_groups = [flag_group(flags = ["/OPT:ICF", "/OPT:REF"])],
                with_features = [with_feature_set(features = ["opt"])],
            ),
        ],
    )

    compiler_input_flags_feature = feature(
        name = "compiler_input_flags",
        flag_sets = [
            flag_set(
                actions = [
                    ACTION_NAMES.assemble,
                    ACTION_NAMES.preprocess_assemble,
                    ACTION_NAMES.c_compile,
                    ACTION_NAMES.cpp_compile,
                    ACTION_NAMES.cpp_header_parsing,
                    ACTION_NAMES.cpp_module_compile,
                    ACTION_NAMES.cpp_module_codegen,
                ],
                flag_groups = [
                    flag_group(
                        flags = ["/c", "%{source_file}"],
                        expand_if_available = "source_file",
                    ),
                ],
            ),
        ],
    )

    def_file_feature = feature(
        name = "def_file",
        flag_sets = [
            flag_set(
                actions = all_link_actions,
                flag_groups = [
                    flag_group(
                        flags = ["/DEF:%{def_file_path}", "/ignore:4070"],
                        expand_if_available = "def_file_path",
                    ),
                ],
            ),
        ],
    )

    if ctx.attr.cpu == "k8":
        features = [
            dependency_file_feature,
            random_seed_feature,
            pic_feature,
            per_object_debug_info_feature,
            preprocessor_defines_feature,
            includes_feature,
            include_paths_feature,
            fdo_instrument_feature,
            cs_fdo_instrument_feature,
            cs_fdo_optimize_feature,
            fdo_prefetch_hints_feature,
            autofdo_feature,
            build_interface_libraries_feature,
            dynamic_library_linker_tool_feature,
            symbol_counts_feature,
            shared_flag_feature,
            linkstamps_feature,
            output_execpath_flags_feature,
            runtime_library_search_directories_feature,
            library_search_directories_feature,
            archiver_flags_feature,
            force_pic_flags_feature,
            fission_support_feature,
            strip_debug_symbols_feature,
            coverage_feature,
            supports_pic_feature,
            default_compile_flags_feature,
            default_link_flags_feature,
            libraries_to_link_feature,
            user_link_flags_feature,
            static_libgcc_feature,
            fdo_optimize_feature,
            supports_dynamic_linker_feature,
            dbg_feature,
            opt_feature,
            user_compile_flags_feature,
            sysroot_feature,
            unfiltered_compile_flags_feature,
            lld_feature,
        ]
    elif ctx.attr.cpu == "darwin":
        features = [
            default_compile_flags_feature,
            default_link_flags_feature,
            supports_dynamic_linker_feature,
            supports_pic_feature,
            objcopy_embed_flags_feature,
            dbg_feature,
            opt_feature,
            user_compile_flags_feature,
            #TODO: get lld to work
            #lld_feature,
            unfiltered_compile_flags_feature,
            archiver_flags_feature,
            macos_target_min_requirement_feature,
        ]
    elif ctx.attr.cpu == "x64_windows":
        features = [
            no_legacy_features_feature,
            nologo_feature,
            has_configured_linker_path_feature,
            no_stripping_feature,
            targets_windows_feature,
            copy_dynamic_libraries_to_binary_feature,
            default_compile_flags_feature,
            msvc_env_feature,
            msvc_compile_env_feature,
            msvc_link_env_feature,
            include_paths_feature,
            preprocessor_defines_feature,
            parse_showincludes_feature,
            generate_pdb_file_feature,
            shared_flag_feature,
            linkstamps_feature,
            output_execpath_flags_feature,
            archiver_flags_feature,
            input_param_flags_feature,
            linker_subsystem_flag_feature,
            user_link_flags_feature,
            default_link_flags_feature,
            linker_param_file_feature,
            static_link_msvcrt_feature,
            static_link_msvcrt_no_debug_feature,
            dynamic_link_msvcrt_no_debug_feature,
            static_link_msvcrt_debug_feature,
            dynamic_link_msvcrt_debug_feature,
            dbg_feature,
            fastbuild_feature,
            opt_feature,
            frame_pointer_feature,
            disable_assertions_feature,
            determinism_feature,
            treat_warnings_as_errors_feature,
            smaller_binary_feature,
            ignore_noisy_warnings_feature,
            user_compile_flags_feature,
            sysroot_feature,
            unfiltered_compile_flags_feature,
            compiler_param_file_feature,
            compiler_output_flags_feature,
            compiler_input_flags_feature,
            def_file_feature,
            windows_export_all_symbols_feature,
            no_windows_export_all_symbols_feature,
            supports_dynamic_linker_feature,
            supports_interface_shared_libraries_feature,
        ]

    return cc_common.create_cc_toolchain_config_info(
        ctx = ctx,
        artifact_name_patterns = artifact_name_patterns,
        features = features,
        action_configs = action_configs,
        cxx_builtin_include_directories = ctx.attr.cxx_builtin_include_directories,
        toolchain_identifier = ctx.attr.toolchain_identifier,
        host_system_name = ctx.attr.host_system_name,
        target_system_name = ctx.attr.target_system_name,
        target_cpu = ctx.attr.cpu,
        target_libc = ctx.attr.target_libc,
        compiler = ctx.attr.compiler,
        abi_version = ctx.attr.abi_version,
        abi_libc_version = ctx.attr.abi_libc_version,
        tool_paths = tool_paths,
    )

cc_toolchain_config = rule(
    implementation = _impl,
    attrs = {
        "cpu": attr.string(mandatory = True),
        "compiler": attr.string(mandatory = True),
        "toolchain_identifier": attr.string(mandatory = True),
        "host_system_name": attr.string(mandatory = True),
        "target_system_name": attr.string(mandatory = True),
        "target_libc": attr.string(mandatory = True),
        "abi_version": attr.string(mandatory = True),
        "abi_libc_version": attr.string(mandatory = True),
        "cxx_builtin_include_directories": attr.string_list(),
        "tool_paths": attr.string_dict(),
        "compile_flags": attr.string_list(),
        "dbg_compile_flags": attr.string_list(),
        "opt_compile_flags": attr.string_list(),
        "cxx_flags": attr.string_list(),
        "link_flags": attr.string_list(),
        "link_libs": attr.string_list(),
        "opt_link_flags": attr.string_list(),
        "unfiltered_compile_flags": attr.string_list(),
        "coverage_compile_flags": attr.string_list(),
        "coverage_link_flags": attr.string_list(),
        "tmp_path": attr.string(),
    },
    provides = [CcToolchainConfigInfo],
)
