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
#include "utils/bash_command.h"
#include "utils/file_reader.h"

using profiler::TestUtils;
using profiler::proto::GraphicsData;

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

class MockBashCommandRunner final : public profiler::BashCommandRunner {
 public:
  MockBashCommandRunner(const std::string &test_data_file_name)
      : BashCommandRunner(""), test_data_file_name_(test_data_file_name) {}
  virtual bool Run(const std::string &parameters,
                   std::string *output) const override {
    if (!test_data_file_name_.empty()) {
      profiler::FileReader::Read(
          TestUtils::getGraphicsTestData(test_data_file_name_), output);
    }
    return true;
  }

 private:
  std::string test_data_file_name_;
};

TEST(GetFrameStatsVector, GetFrameStatsVectorValidOutput) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::vector<GraphicsData> data_vector;
  MockBashCommandRunner cmd_runner("graphics_data_valid_frames.txt");
  int64_t last_timestamp =
      sampler.GetFrameStatsVector(0, cmd_runner, &data_vector);

  // Should be the last time stamp.
  EXPECT_EQ(96072491739919, last_timestamp);
  // 127 frames.
  EXPECT_EQ(127, data_vector.size());

  EXPECT_EQ(96070354631117,
            data_vector.at(0).frame_stats().app_draw_timestamp());
  EXPECT_EQ(96070372447472, data_vector.at(0).frame_stats().vsync_timestamp());
  EXPECT_EQ(96070354631117, data_vector.at(0).frame_stats().set_timestamp());
}

TEST(GetFrameStatsVector, GetFrameStatsVectorSomeZerosOutput) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::vector<GraphicsData> data_vector;
  MockBashCommandRunner cmd_runner("graphics_data_has_zeros.txt");
  int64_t last_timestamp =
      sampler.GetFrameStatsVector(0, cmd_runner, &data_vector);

  // Should be the last time stamp.
  EXPECT_EQ(96047538178834, last_timestamp);
  // Number of non-zero frames.
  EXPECT_EQ(36, data_vector.size());

  EXPECT_EQ(96046918780657,
            data_vector.at(0).frame_stats().app_draw_timestamp());
  EXPECT_EQ(96046929649824, data_vector.at(0).frame_stats().vsync_timestamp());
  EXPECT_EQ(96046918780657, data_vector.at(0).frame_stats().set_timestamp());
}

TEST(GetFrameStatsVector, GetFrameStatsVectorSingleNumberOutput) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::vector<GraphicsData> data_vector;
  MockBashCommandRunner cmd_runner("graphics_data_single_number.txt");
  int64_t last_timestamp =
      sampler.GetFrameStatsVector(0, cmd_runner, &data_vector);

  // 0 because no frames present.
  EXPECT_EQ(0, last_timestamp);
  // 0 because no frames present.
  EXPECT_EQ(0, data_vector.size());
}

TEST(GetFrameStatsVector, GetFrameStatsVectorAllZeroOutput) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::vector<GraphicsData> data_vector;
  MockBashCommandRunner cmd_runner("graphics_data_has_all_zeros.txt");
  int64_t last_timestamp =
      sampler.GetFrameStatsVector(0, cmd_runner, &data_vector);

  // 0 because no frames present.
  EXPECT_EQ(0, last_timestamp);
  // 0 because no frames present.
  EXPECT_EQ(0, data_vector.size());
}

TEST(GetFrameStatsVector, GetFrameStatsVectorEmptyOutput) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::vector<GraphicsData> data_vector;
  MockBashCommandRunner cmd_runner("");
  int64_t last_timestamp =
      sampler.GetFrameStatsVector(0, cmd_runner, &data_vector);

  // 0 because no frames present.
  EXPECT_EQ(0, last_timestamp);
  // 0 because no frames present.
  EXPECT_EQ(0, data_vector.size());
}

TEST(GetFrameStatsVector, GetFrameStatsVectorInvalidOutput) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::vector<GraphicsData> data_vector;
  MockBashCommandRunner cmd_runner("graphics_data_invalid.txt");
  int64_t last_timestamp =
      sampler.GetFrameStatsVector(0, cmd_runner, &data_vector);

  // 0 because no frames present.
  EXPECT_EQ(0, last_timestamp);
  // 0 because no frames present.
  EXPECT_EQ(0, data_vector.size());
}

TEST(GetFrameStatsVector, GetFrameStatsVectorMaxLongOutput) {
  profiler::GraphicsFrameStatsSampler sampler;
  std::vector<GraphicsData> data_vector;
  MockBashCommandRunner cmd_runner("graphics_data_has_max_long.txt");
  int64_t last_timestamp =
      sampler.GetFrameStatsVector(0, cmd_runner, &data_vector);

  // Should be the 2nd to last time stamp because last sample invalid
  EXPECT_EQ(96072474911481, last_timestamp);
  // 127 frames minus the last invalid one.
  EXPECT_EQ(126, data_vector.size());
}