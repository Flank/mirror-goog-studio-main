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
#ifndef PERFD_GRAPHICS_GRAPHICS_CACHE_H_
#define PERFD_GRAPHICS_GRAPHICS_CACHE_H_

#include <mutex>
#include <string>

#include "proto/graphics.pb.h"
#include "utils/circular_buffer.h"
#include "utils/clock.h"

namespace profiler {

// Class to provide a graphics data saving and loading interface.
class GraphicsCache {
 public:
  explicit GraphicsCache(const Clock& clock, int32_t capacity);

  void SaveGraphicsDataVector(
      const std::vector<profiler::proto::GraphicsData> data_vector);

  void LoadGraphicsData(int64_t start_time_exl, int64_t end_time_inc,
                        profiler::proto::GraphicsDataResponse* response);

 private:
  const Clock& clock_;
  CircularBuffer<profiler::proto::GraphicsData> graphics_samples_;
  std::mutex graphics_samples_mutex_;
};

}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_CACHE_H_
