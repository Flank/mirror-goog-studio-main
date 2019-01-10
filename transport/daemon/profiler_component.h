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
#ifndef PERFD_PROFILER_COMPONENT_H_
#define PERFD_PROFILER_COMPONENT_H_

#include <grpc++/grpc++.h>
#include <climits>

namespace profiler {

// The interface of a profiler component in perfd.
class ProfilerComponent {
 public:
  // Ensures a subclass's destructor is called when deleted from a base pointer.
  virtual ~ProfilerComponent() {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  virtual grpc::Service* GetPublicService() = 0;

  // Returns the service that talks to device clients (e.g., the agent).
  virtual grpc::Service* GetInternalService() = 0;

  // Returns the earliest time when data was available for the given |pid|. If
  // no data is available yet, it should return LLONG_MAX. Overriding this
  // method is useful when the |ProfilerComponent| receives data before a
  // session begins (i.e startup CPU profiling).
  virtual int64_t GetEarliestDataTime(int32_t pid) { return LLONG_MAX; }
};

}  // namespace profiler

#endif  // PERFD_PROFILER_COMPONENT_H_
