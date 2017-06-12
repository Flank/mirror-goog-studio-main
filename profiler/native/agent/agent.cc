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
#include "agent.h"

#include <sys/types.h>
#include <unistd.h>
#include <cassert>
#include <mutex>
#include <sstream>
#include <string>

#include <grpc++/support/channel_arguments.h>
#include "utils/config.h"
#include "utils/device_info.h"
#include "utils/log.h"
#include "utils/socket_utils.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"

namespace {
// If the agent is disconnected from perfd, grpc requests will begin backing up.
// Given that downloading a 1MB image would send 1000 1K chunk messages (plus
// general network event messages), it seems reasonable to limit our queue to
// something one or two magnitudes above that, to be safe.
const int32_t kMaxBackgroundTasks = 100000;  // Worst case: ~100MB in memory
}  // namespace

namespace profiler {

using grpc::Status;
using proto::AgentService;
using proto::CommonData;
using proto::HeartBeatResponse;
using proto::InternalEventService;
using proto::InternalNetworkService;
using std::lock_guard;

Agent& Agent::Instance(SocketType socket_type) {
  static Agent* instance = new Agent(socket_type);
  return *instance;
}

Agent::Agent(SocketType socket_type)
    : memory_component_(nullptr),  // set in ConnectToPerfd()
      background_queue_("Studio:Agent", kMaxBackgroundTasks),
      current_fd_(-1),
      can_grpc_target_change_(false),
      grpc_target_initialized_(false) {
  if (profiler::DeviceInfo::feature_level() >= 26 &&
      socket_type == SocketType::kAbstractSocket) {
    // For O and post-O devices, we used an existing socket of which the file
    // descriptor will be provided into kAgentSocketName. This is provided via
    // socket_thread_ so we don't setup here.
    can_grpc_target_change_ = true;
    socket_thread_ = std::thread(&Agent::RunSocketThread, this);
  } else {
    // Pre-O, we don't need to start the socket thread, as the agent
    // communicates to perfd via a fixed port.
    ConnectToPerfd(kServerAddress);
  }

  heartbeat_thread_ = std::thread(&Agent::RunHeartbeatThread, this);
}

void Agent::SubmitNetworkTasks(const std::vector<NetworkServiceTask>& tasks) {
  background_queue_.EnqueueTask([this, tasks] {
    for (auto task : tasks) {
      if (can_grpc_target_change_) {
        bool success = false;
        do {
          // Each grpc call needs a new ClientContext.
          grpc::ClientContext ctx;
          Config::SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
          Status status = task(network_stub(), ctx);
          success = status.ok();
        } while (!success);
      } else {
        grpc::ClientContext ctx;
        task(network_stub(), ctx);
      }
    }
  });
}

void Agent::SubmitEventTasks(const std::vector<EventServiceTask>& tasks) {
  background_queue_.EnqueueTask([this, tasks] {
    for (auto task : tasks) {
      if (can_grpc_target_change_) {
        bool success = false;
        do {
          // Each grpc call needs a new ClientContext.
          grpc::ClientContext ctx;
          Config::SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
          Status status = task(event_stub(), ctx);
          success = status.ok();
        } while (!success);
      } else {
        grpc::ClientContext ctx;
        task(event_stub(), ctx);
      }
    }
  });
}

proto::AgentService::Stub& Agent::agent_stub() {
  std::unique_lock<std::mutex> lock(connect_mutex_);
  while (!grpc_target_initialized_ || agent_stub_.get() == nullptr) {
    connect_cv_.wait(lock);
  }
  return *(agent_stub_.get());
}

proto::InternalEventService::Stub& Agent::event_stub() {
  std::unique_lock<std::mutex> lock(connect_mutex_);
  while (!grpc_target_initialized_ || event_stub_.get() == nullptr) {
    connect_cv_.wait(lock);
  }
  return *(event_stub_.get());
}

proto::InternalNetworkService::Stub& Agent::network_stub() {
  std::unique_lock<std::mutex> lock(connect_mutex_);
  while (!grpc_target_initialized_ || network_stub_.get() == nullptr) {
    connect_cv_.wait(lock);
  }
  return *(network_stub_.get());
}

MemoryComponent& Agent::memory_component() {
  std::unique_lock<std::mutex> lock(connect_mutex_);
  while (!grpc_target_initialized_ || memory_component_ == nullptr) {
    connect_cv_.wait(lock);
  }
  return *memory_component_;
}

void Agent::AddPerfdStatusChangedCallback(PerfdStatusChanged callback) {
  lock_guard<std::mutex> guard(callback_mutex_);
  perfd_status_changed_callbacks_.push_back(callback);
}

void Agent::RunHeartbeatThread() {
  SetThreadName("Studio:Heartbeat");
  Stopwatch stopwatch;
  bool was_perfd_alive = false;
  while (true) {
    int64_t start_ns = stopwatch.GetElapsed();
    // TODO: handle erroneous status
    HeartBeatResponse response;
    grpc::ClientContext context;

    // Set a deadline on the context, so we can get a proper status code if
    // perfd is not connected.
    std::chrono::nanoseconds offset(kHeartBeatIntervalNs * 2);
    std::chrono::system_clock::time_point deadline =
        std::chrono::system_clock::now();

    // Linux and Mac are slightly different with respect to default accuracy of
    // time_point Linux is nanoseconds, mac is milliseconds so we cater to the
    // lowest common.
    deadline += std::chrono::duration_cast<std::chrono::milliseconds>(offset);
    context.set_deadline(deadline);
    CommonData data;
    data.set_process_id(getpid());

    // Status returns OK if it succeeds, else it returns a standard grpc error
    // code.
    const grpc::Status status =
        agent_stub().HeartBeat(&context, data, &response);

    int64_t elapsed_ns = stopwatch.GetElapsed() - start_ns;
    // Use status to determine if perfd is alive.
    bool is_perfd_alive = status.ok();
    if (kHeartBeatIntervalNs > elapsed_ns) {
      int64_t sleep_us = Clock::ns_to_us(kHeartBeatIntervalNs - elapsed_ns);
      usleep(static_cast<uint64_t>(sleep_us));
    }

    if (is_perfd_alive != was_perfd_alive) {
      lock_guard<std::mutex> guard(callback_mutex_);
      for (auto callback : perfd_status_changed_callbacks_) {
        callback(is_perfd_alive);
      }
      was_perfd_alive = is_perfd_alive;
    }
  }
}

void Agent::RunSocketThread() {
  SetThreadName("Studio:Socket");

  // Creates and listens to socket at kAgentSocketName+pid.
  std::ostringstream app_socket_name;
  app_socket_name << kAgentSocketName << getpid();
  int socket_fd =
      ListenToSocket(CreateUnixSocket(app_socket_name.str().c_str()));

  int buffer_length = 1;
  while (true) {
    int receive_fd;
    char buf[buffer_length];
    // Try to get next message with a 1-second timeout.
    int read_count = AcceptAndGetDataFromSocket(socket_fd, &receive_fd, buf,
                                                buffer_length, 1, 0);
    if (read_count > 0) {
      if (strncmp(buf, kHeartBeatRequest, 1) == 0) {
        // Heartbeat - No-op. Perfd will check whether send was successful.
      } else if (strncmp(buf, kPerfdConnectRequest, 1) == 0) {
        // A connect request - reconnect using the incoming fd.
        // Note: If the same fd is being reused, do not reconnect as the
        // 'previous' fd will be closed, and the re-instantiated stubs will
        // point to a closed target.
        if (current_fd_ != receive_fd) {
          std::ostringstream os;
          os << kGrpcUnixSocketAddrPrefix << "&" << receive_fd;
          ConnectToPerfd(os.str());
          current_fd_ = receive_fd;
        }
      }
    }
  }
}

void Agent::ConnectToPerfd(const std::string& target) {
  // Synchronization is needed around the (re)initialization of all
  // services to prevent a task to acquire a service stub but gets freed
  // below.
  lock_guard<std::mutex> guard(connect_mutex_);

  if (memory_component_ == nullptr) {
    memory_component_ =
        new MemoryComponent(&background_queue_, can_grpc_target_change_);
  }

  // Override default channel arguments in gRPC to limit the reconnect delay
  // to 1 second when the daemon is unavailable. GRPC's default arguments may
  // have backoff as long as 120 seconds. In cases when the phone is
  // disconnected and plugged back in, a 120-second-long delay hurts the user
  // experience very much.
  grpc::ChannelArguments channel_args;
  channel_args.SetInt(GRPC_ARG_MAX_RECONNECT_BACKOFF_MS, 1000);
  auto channel = grpc::CreateCustomChannel(
      target, grpc::InsecureChannelCredentials(), channel_args);

  agent_stub_ = AgentService::NewStub(channel);
  event_stub_ = InternalEventService::NewStub(channel);
  network_stub_ = InternalNetworkService::NewStub(channel);
  memory_component_->Connect(channel);

  if (!grpc_target_initialized_) {
    grpc_target_initialized_ = true;
    // Service stubs are null before the first time this method is called.
    // Any tasks that call *_stub() during that time are blocked by
    // |connect_cv_| to avoid having to handle nullptr scenarios. Notify
    // all tasks that have been called once everything has been initialized
    // the first time.
    connect_cv_.notify_all();
  }
}

}  // namespace profiler
