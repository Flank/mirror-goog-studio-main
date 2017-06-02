/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "graphics_framestats_sampler.h"

#include <gtest/gtest.h>

#include "proto/graphics.pb.h"
#include "test/utils.h"
#include "utils/file_reader.h"

using profiler::TestUtils;

TEST(GetDumpsysCommand, DumpsysCommandValid24) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::string command = sampler.GetDumpsysCommand("app/name", 24);

  EXPECT_EQ("dumpsys SurfaceFlinger --latency \"SurfaceView - app/name\"",
            command);
}

TEST(GetDumpsysCommand, DumpsysCommandValid21) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::string command = sampler.GetDumpsysCommand("app/name", 21);

  EXPECT_EQ("dumpsys SurfaceFlinger --latency \"SurfaceView\"", command);
}