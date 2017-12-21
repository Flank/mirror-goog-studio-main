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
#ifndef PERFD_MEMORY_INTERNAL_MEMORY_SERVICE_H_
#define PERFD_MEMORY_INTERNAL_MEMORY_SERVICE_H_

#include <grpc++/grpc++.h>
#include <condition_variable>
#include <map>
#include <mutex>
#include <unordered_map>

#include "memory_collector.h"
#include "perfd/sessions/sessions_manager.h"
#include "proto/internal_memory.grpc.pb.h"

namespace profiler {

class InternalMemoryServiceImpl final
    : public proto::InternalMemoryService::Service {
 public:
  explicit InternalMemoryServiceImpl(
      const SessionsManager &sessions,
      std::unordered_map<int64_t, MemoryCollector> *collectors)
      : sessions_(sessions), collectors_(*collectors) {}
  virtual ~InternalMemoryServiceImpl() = default;

  grpc::Status RegisterMemoryAgent(
      grpc::ServerContext *context,
      const proto::RegisterMemoryAgentRequest *request,
      grpc::ServerWriter<proto::MemoryControlRequest> *writer) override;

  grpc::Status RecordAllocStats(grpc::ServerContext *context,
                                const proto::AllocStatsRequest *request,
                                proto::EmptyMemoryReply *reply) override;

  grpc::Status RecordGcStats(grpc::ServerContext *context,
                             const proto::GcStatsRequest *request,
                             proto::EmptyMemoryReply *reply) override;

  grpc::Status RecordAllocationEvents(
      grpc::ServerContext *context, const proto::BatchAllocationSample *request,
      proto::EmptyMemoryReply *reply) override;

  grpc::Status RecordJNIRefEvents(grpc::ServerContext *context,
                                  const proto::BatchJNIGlobalRefEvent *request,
                                  proto::EmptyMemoryReply *reply) override;

  /**
   * Sends a MemoryControlRequest to the profiling agent.
   * Returns true if the signal is sent, false otherwise (if the agent
   * is not alive). This method is protected so only one thread
   * can send a signal to an app at a time.
   */
  bool SendRequestToAgent(const proto::MemoryControlRequest &request);

 private:
  // Finds the currently active session based on |pid|. Returns true if
  // a session is found, false otherwise.
  bool FindSession(int32_t pid, proto::Session *session);
  // Finds the MemoryCollector associated with the currently active session
  // based on |pid|. Returns true if found, false otherwise.
  bool FindCollector(int32_t pid, MemoryCollector **collector);

  std::mutex status_mutex_;
  std::mutex control_mutex_;
  std::condition_variable control_cv_;

  // Used for converting pid's received from perfa to session id's which
  // are what used to identify profiling sessions in perfd and studio.
  const SessionsManager &sessions_;

  // Maps session id to MemoryCollector
  std::unordered_map<int64_t, MemoryCollector> &collectors_;

  // Per-app flag which indicates whether a perfd->perfa grpc streaming
  // call (RegisterMemoryAgent) has been established. Value is true if a stream
  // is alive, false otherwise.
  std::map<int32_t, bool> app_control_stream_statuses_;
  std::map<int32_t, proto::MemoryControlRequest> pending_control_requests_;
};

}  // namespace profiler

#endif  // PERFD_MEMORY_INTERNAL_MEMORY_SERVICE_H_
