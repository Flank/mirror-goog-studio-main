# Copyright (C) 2019 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
package(default_visibility = ["//visibility:public"])

load("@//tools/base/bazel:proto.bzl", "cc_grpc_proto_library", "java_proto_library")

java_proto_library(
    name = "java_proto",
    srcs = glob(["**/perfetto_trace.proto"]),
    grpc_support = 1,
)

cc_grpc_proto_library(
    name = "cc_proto",
    srcs = glob(["**/perfetto_config.proto"]),
    grpc_support = 1,
    tags = ["no_windows"],
)
