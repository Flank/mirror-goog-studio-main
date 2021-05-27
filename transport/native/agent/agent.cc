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

#include <google/protobuf/util/message_differencer.h>
#include <grpc++/support/channel_arguments.h>
#include <grpc/support/log.h>
#include <limits.h>
#include <sys/types.h>
#include <unistd.h>

#include <cassert>
#include <mutex>
#include <sstream>
#include <string>

#include "proto/transport.grpc.pb.h"
#include "utils/device_info.h"
#include "utils/fd_utils.h"
#include "utils/log.h"
#include "utils/socket_utils.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"
#include "utils/trace.h"

namespace {
// If the agent is disconnected from daemon, grpc requests will begin backing
// up. Given that downloading a 1MB image would send 1000 1K chunk messages
// (plus general network event messages), it seems reasonable to limit our queue
// to something one or two magnitudes above that, to be safe.
const int32_t kMaxBackgroundTasks = 100000;  // Worst case: ~100MB in memory
}  // namespace

namespace profiler {

using grpc::Status;
using proto::AgentConfig;
using proto::AgentService;
using proto::CommonConfig;
using proto::EmptyResponse;
using proto::HeartBeatRequest;
using proto::InternalCpuService;
using proto::InternalEnergyService;
using proto::InternalEventService;
using proto::InternalNetworkService;
using std::lock_guard;

Agent& Agent::Instance(bool replace, const AgentConfig& config) {
  Trace trace("Studio Agent ctor");
  static Agent* instance = new Agent(config);
  if (replace && !google::protobuf::util::MessageDifferencer::Equals(
                     config, instance->agent_config())) {
    delete instance;
    instance = new Agent(config);
  }
  return *instance;
}

Agent::Agent(const AgentConfig& config)
    : agent_config_(config),
      background_queue_("Studio:Agent", kMaxBackgroundTasks),
      can_grpc_target_change_(false),
      grpc_target_initialized_(false),
      memory_component_(nullptr),  // set in ConnectToDaemon()
      profiler_initialized_(false),
      running_(true) {
  if (agent_config_.common().socket_type() == CommonConfig::ABSTRACT_SOCKET) {
    // We use an existing socket of which the file descriptor will be provided
    // into kAgentSocketName. This is provided via socket_thread_ so we don't
    // setup here.
    can_grpc_target_change_ = true;
    socket_thread_ = std::thread(&Agent::RunSocketThread, this);
  } else {
    // Otherwise the agent communicates to daemon via a fixed port.
    ConnectToDaemon(agent_config_.common().service_address());
    // In Pre-O, only profilers can attach agent so we can initialize profilers
    // now.
    if (profiler::DeviceInfo::feature_level() < DeviceInfo::O) {
      InitializeProfilers();
    }
    StartHeartbeat();
  }

#ifdef NDEBUG
  gpr_set_log_verbosity(static_cast<gpr_log_severity>(SHRT_MAX));
#endif
}

Agent::~Agent() {
  running_ = false;
  if (heartbeat_thread_.joinable()) {
    heartbeat_thread_.join();
  }
  if (socket_thread_.joinable()) {
    socket_thread_.join();
  }
  if (command_handler_thread_.joinable()) {
    command_handler_thread_.join();
  }
}

void Agent::StartHeartbeat() {
  if (heartbeat_thread_.joinable()) {
    return;
  }
  heartbeat_thread_ = std::thread(&Agent::RunHeartbeatThread, this);
}

void Agent::InitializeProfilers() {
  AddDaemonConnectedCallback([this] {
    std::lock_guard<std::mutex> profiler_guard(profiler_mutex_);
    profiler_initialized_ = true;
    if (memory_component_ == nullptr) {
      memory_component_ =
          new MemoryComponent(&background_queue_, can_grpc_target_change_);
    }
    memory_component_->Connect(channel_);
    profiler_cv_.notify_all();
  });
}

bool Agent::IsProfilerInitalized() { return profiler_initialized_; }

void Agent::SubmitAgentTasks(const std::vector<AgentServiceTask>& tasks) {
  background_queue_.EnqueueTask([this, tasks] {
    for (auto task : tasks) {
      if (can_grpc_target_change_) {
        bool error_per_task_logged = false;
        bool success = false;
        do {
          // Each grpc call needs a new ClientContext.
          grpc::ClientContext ctx;
          SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
          Status status = task(agent_stub(), ctx);
          if (!error_per_task_logged && !status.ok()) {
            Log::E(Log::Tag::TRANSPORT,
                   "Agent::SubmitAgentTasks error_code=%d '%s' '%s'",
                   status.error_code(), status.error_message().data(),
                   status.error_details().data());
            error_per_task_logged = true;
          }
          success = status.ok();
        } while (!success);
      } else {
        grpc::ClientContext ctx;
        task(agent_stub(), ctx);
      }
    }
  });
}

void Agent::SubmitNetworkTasks(const std::vector<NetworkServiceTask>& tasks) {
  background_queue_.EnqueueTask([this, tasks] {
    for (auto task : tasks) {
      if (can_grpc_target_change_) {
        bool success = false;
        do {
          // Each grpc call needs a new ClientContext.
          grpc::ClientContext ctx;
          SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
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
          SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
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

void Agent::SubmitEnergyTasks(const std::vector<EnergyServiceTask>& tasks) {
  background_queue_.EnqueueTask([this, tasks] {
    for (auto task : tasks) {
      if (can_grpc_target_change_) {
        bool success = false;
        do {
          // Each grpc call needs a new ClientContext.
          grpc::ClientContext ctx;
          SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
          Status status = task(energy_stub(), ctx);
          success = status.ok();
        } while (!success);
      } else {
        grpc::ClientContext ctx;
        task(energy_stub(), ctx);
      }
    }
  });
}

void Agent::SubmitCpuTasks(const std::vector<CpuServiceTask>& tasks) {
  background_queue_.EnqueueTask([this, tasks] {
    for (auto task : tasks) {
      if (can_grpc_target_change_) {
        bool success = false;
        do {
          // Each grpc call needs a new ClientContext.
          grpc::ClientContext ctx;
          SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
          Status status = task(cpu_stub(), ctx);
          success = status.ok();
        } while (!success);
      } else {
        grpc::ClientContext ctx;
        task(cpu_stub(), ctx);
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

proto::InternalCpuService::Stub& Agent::cpu_stub() {
  std::unique_lock<std::mutex> lock(connect_mutex_);
  while (!grpc_target_initialized_ || cpu_stub_.get() == nullptr) {
    connect_cv_.wait(lock);
  }
  return *(cpu_stub_.get());
}

proto::InternalEnergyService::Stub& Agent::energy_stub() {
  std::unique_lock<std::mutex> lock(connect_mutex_);
  while (!grpc_target_initialized_ || energy_stub_.get() == nullptr) {
    connect_cv_.wait(lock);
  }
  return *(energy_stub_.get());
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

MemoryComponent& Agent::wait_and_get_memory_component() {
  std::unique_lock<std::mutex> lock(profiler_mutex_);
  while (memory_component_ == nullptr) {
    profiler_cv_.wait(lock);
  }
  return *memory_component_;
}

void Agent::AddDaemonStatusChangedCallback(DaemonStatusChanged callback) {
  lock_guard<std::mutex> guard(callback_mutex_);
  daemon_status_changed_callbacks_.push_back(callback);
}

void Agent::AddDaemonConnectedCallback(std::function<void()> callback) {
  lock_guard<std::mutex> connect_guard(connect_mutex_);
  if (grpc_target_initialized_) {
    background_queue_.EnqueueTask([callback] { callback(); });
  }
  lock_guard<std::mutex> daemon_connected_guard(daemon_connected_mutex_);
  daemon_connected_callbacks_.push_back(callback);
}

void Agent::RunHeartbeatThread() {
  SetThreadName("Studio:Heartbeat");
  Stopwatch stopwatch;
  bool was_daemon_alive = false;
  while (running_) {
    // agent_stub() is blocking while the stub is unavailable. If we don't call
    // it here and rely only on the one right before calling HeartBeat(),
    // |start_ns| and gPRC's deadline may likely include the time being blocked,
    // defecting their purposes.
    agent_stub();
    int64_t start_ns = stopwatch.GetElapsed();
    EmptyResponse response;
    grpc::ClientContext context;
    // Linux and Mac are slightly different with respect to default accuracy of
    // time_point Linux is nanoseconds, mac is milliseconds so we cater to the
    // lowest common.
    SetClientContextTimeout(&context, 0,
                            Clock::ns_to_ms(kHeartBeatIntervalNs) * 2);
    HeartBeatRequest request;
    request.set_pid(getpid());

    // Status returns OK if it succeeds (daemon is alive), else it returns a
    // standard grpc error code.
    const grpc::Status status =
        agent_stub().HeartBeat(&context, request, &response);
    bool is_daemon_alive = status.ok();
    if (is_daemon_alive != was_daemon_alive) {
      lock_guard<std::mutex> guard(callback_mutex_);
      for (auto itor = daemon_status_changed_callbacks_.begin();
           itor != daemon_status_changed_callbacks_.end();) {
        auto callback = *itor;
        if (callback(is_daemon_alive)) {
          itor = daemon_status_changed_callbacks_.erase(itor);
        } else {
          itor++;
        }
      }
      was_daemon_alive = is_daemon_alive;
    }

    int64_t elapsed_ns = stopwatch.GetElapsed() - start_ns;
    if (kHeartBeatIntervalNs > elapsed_ns) {
      int64_t sleep_us = Clock::ns_to_us(kHeartBeatIntervalNs - elapsed_ns);
      usleep(static_cast<uint64_t>(sleep_us));
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
  while (running_) {
    int receive_fd;
    char buf[buffer_length];
    // Try to get next message with a 1-second timeout.
    int read_count = AcceptAndGetDataFromSocket(socket_fd, &receive_fd, buf,
                                                buffer_length, 1, 0);
    if (read_count > 0) {
      if (strncmp(buf, kHeartBeatRequest, 1) == 0) {
        // Heartbeat - No-op. Daemon will check whether send was successful.
      } else if (strncmp(buf, kDaemonConnectRequest, 1) == 0) {
        // A connect request - reconnect using the incoming fd.
        Log::D(Log::Tag::TRANSPORT,
               "Receiving kDaemonConnectRequest, receive_fd=%d current_fd_=%d",
               receive_fd, current_fd_);
        int fd = receive_fd;
        // Note: In case the same fd is being reused, when the old GRPC channel
        // is recycled (it's a shared_ptr<>), it seems the 'previous' fd would
        // be closed, and the re-instantiated stubs will point to a closed
        // target. Therefore, we duplicate the fd to force it to be different.
        if (current_fd_ != -1) {
          if (current_fd_ == receive_fd) {
            fd = dup(receive_fd);
          }
          CloseFdAndLogAtError(current_fd_);
          current_fd_ = -1;
        }
        std::ostringstream os;
        os << kGrpcUnixSocketAddrPrefix << "&" << fd;
        ConnectToDaemon(os.str());
        // Cannot close the fd here even though the agent owns it (it will be
        // dup()ed by gRPC library). The dup() happens when the target is used
        // by the first gRPC call which may happen in another thread (e.g.,
        // heartbeat).
        current_fd_ = fd;
        AddDaemonStatusChangedCallback([this](bool becomes_alive) {
          if (becomes_alive) return false;  // don't remove this callback

          // The daemon is no longer alive; the grpc target is no longer valid.
          // grpc_target_initialized_ being false would pause subsequent gRPC
          // calls until the daemon is reconnected.
          std::unique_lock<std::mutex> lock(connect_mutex_);
          grpc_target_initialized_ = false;
          // We should close the fd because it's a client connecting to the
          // abstract domain socket. Otherwise, the open reference might prevent
          // the socket from disappearing which would prevent the daemon from
          // successful restarting.
          if (current_fd_ != -1) {
            CloseFdAndLogAtError(current_fd_);
            current_fd_ = -1;
          }
          return true;  // remove this callback if execution reaches here
        });
      }
    }
  }
}

void Agent::RunCommandHandlerThread() {
  SetThreadName("Studio:CmdHdler");

  grpc::ClientContext context;
  proto::RegisterAgentRequest request;
  request.set_pid(getpid());
  std::unique_ptr<grpc::ClientReader<proto::Command>> reader =
      agent_stub().RegisterAgent(&context, request);
  Log::V(Log::Tag::TRANSPORT, "Agent command stream started.");

  proto::Command command;
  while (reader->Read(&command)) {
    auto search = command_handlers_.find(command.type());
    if (search != command_handlers_.end()) {
      Log::V(Log::Tag::TRANSPORT, "Handling agent command %d for pid: %d.",
             command.type(), command.pid());
      (search->second)(&command);
    }
  }
  // If Read() returns false, it indicates the server (daemon) is dead
  // because this streaming gRPC is expected to last as long as the server and
  // client are both alive.
  Log::D(Log::Tag::TRANSPORT, "Streaming gRPC call Read() returns false");
}

void Agent::ConnectToDaemon(const std::string& target) {
  // Synchronization is needed around the (re)initialization of all
  // services to prevent a task to acquire a service stub but gets freed
  // below.
  lock_guard<std::mutex> guard(connect_mutex_);
  Log::V(Log::Tag::TRANSPORT, "Create gRPC channel on fd-based target '%s'",
         target.c_str());

  // Override default channel arguments in gRPC to limit the reconnect delay
  // to 1 second when the daemon is unavailable. GRPC's default arguments may
  // have backoff as long as 120 seconds. In cases when the phone is
  // disconnected and plugged back in, a 120-second-long delay hurts the user
  // experience very much.
  grpc::ChannelArguments channel_args;
  channel_args.SetInt(GRPC_ARG_MAX_RECONNECT_BACKOFF_MS, 1000);
  channel_ = grpc::CreateCustomChannel(
      target, grpc::InsecureChannelCredentials(), channel_args);

  agent_stub_ = AgentService::NewStub(channel_);
  cpu_stub_ = InternalCpuService::NewStub(channel_);
  energy_stub_ = InternalEnergyService::NewStub(channel_);
  event_stub_ = InternalEventService::NewStub(channel_);
  network_stub_ = InternalNetworkService::NewStub(channel_);

  OpenCommandStream();

  if (!grpc_target_initialized_) {
    grpc_target_initialized_ = true;
    // Service stubs are null before the first time this method is called.
    // Any tasks that call *_stub() during that time are blocked by
    // |connect_cv_| to avoid having to handle nullptr scenarios. Notify
    // all tasks that have been called once everything has been initialized
    // the first time.
    connect_cv_.notify_all();
    background_queue_.EnqueueTask([this] {
      lock_guard<std::mutex> guard(daemon_connected_mutex_);
      for (auto callback : daemon_connected_callbacks_) {
        callback();
      }
    });
  }
}

void Agent::OpenCommandStream() {
  if (command_handler_thread_.joinable()) {
    command_handler_thread_.join();
  }
  command_handler_thread_ = std::thread(&Agent::RunCommandHandlerThread, this);
}
}  // namespace profiler
