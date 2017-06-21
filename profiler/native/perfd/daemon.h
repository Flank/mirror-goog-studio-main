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
#ifndef PERFD_DAEMON_H_
#define PERFD_DAEMON_H_

#include <string>
#include <vector>

#include <grpc++/grpc++.h>

#include "perfd/profiler_component.h"
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/file_cache.h"

namespace profiler {

// A daemon running on the device, collecting, caching, and transporting
// profiling data. It also includes a gRPC server. The gRPC server contains a
// number of gRPC services, including 'public' ones that talk to desktop (e.g.,
// Studio) and 'internal' ones that talk to app processes.
class Daemon {
 public:
  class Utilities {
   public:
    // |config_path| is a string that points to a file that can be parsed by
    // profiler::proto::AgentConfig. If the |config_path| is empty, the file
    // will fail to load and an empty config will be used.
    explicit Utilities(const std::string& config_path) : config_(config_path) {}

    // Returns a const reference to the daemon's clock, which is used to produce
    // all timestamps in the deamon.
    const Clock& clock() const { return clock_; }

    // Shared cache available to all profiler services. Useful for storing data
    // which is
    // 1) large and needs to be cleaned up automatically, or
    // 2) repetitive, and you'd rather send a key to the client each time
    //    instead of the full byte string.
    FileCache* file_cache() { return &file_cache_; }

    // Returns a const reference to the config object to access all
    // configuration parameters.
    const Config& config() { return config_; }

   private:
    // Clock that timestamps profiling data.
    SteadyClock clock_;
    // A shared cache for all profiler services
    FileCache file_cache_;
    // Config object for profiling settings
    Config config_;
  };

  // |config_path| is a string that points to a file that can be parsed by
  // profiler::proto::AgentConfig. If the |config_path| is empty, the file
  // will fail to load and an empty config will be used.
  Daemon(const std::string& config_path) : utilities_(config_path) {}

  // Registers profiler |component| to the daemon, in particular, the
  // component's public and internal services to daemon's server |builder|.
  // Assumes callers are from the same thread. |component| may not be null.
  // |component| cannot be 'const &' because we need to call its non-const
  // methods that return 'Service*'.
  void RegisterComponent(ProfilerComponent* component);

  // Starts running server at |server_address| with the services that have been
  // registered.
  // Block waiting for the server to shutdown. Note that some other thread must
  // be responsible for shutting down the server for this call to ever return.
  void RunServer(const std::string& server_address);

  // Return daemon utilities that should be shared across all profilers.
  Utilities& utilities() { return utilities_; }

 private:
  // Builder of the gRPC server.
  grpc::ServerBuilder builder_;
  // Profiler components that have been registered.
  std::vector<ProfilerComponent*> components_{};
  // Utility classes that should be shared across all profiler services.
  Utilities utilities_;
};

}  // namespace profiler

#endif  // PERFD_DAEMON_H_
