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
#include "perfd/profiler_service.h"

#include <sys/time.h>

#include "utils/android_studio_version.h"
#include "utils/trace.h"

using grpc::ServerContext;
using grpc::ServerWriter;
using grpc::Status;

namespace profiler {

Status ProfilerServiceImpl::GetTimes(
    ServerContext* context, const profiler::proto::TimesRequest* request,
    profiler::proto::TimesResponse* response) {
  Trace trace("PRO:GetTimes");

  response->set_timestamp_ns(clock_.GetCurrentTime());
  // TODO: Move this to utils.
  timeval time;
  gettimeofday(&time, NULL);
  // Not specifying LL may cause overflow depending on the underlying type of
  // time.tv_sec.
  int64_t t = time.tv_sec * 1000000LL + time.tv_usec;
  response->set_epoch_timestamp_us(t);
  return Status::OK;
}

Status ProfilerServiceImpl::GetVersion(
    ServerContext* context, const profiler::proto::VersionRequest* request,
    profiler::proto::VersionResponse* response) {
  response->set_version(profiler::kAndroidStudioVersion);
  return Status::OK;
}

Status ProfilerServiceImpl::GetBytes(
    ServerContext* context, const profiler::proto::BytesRequest* request,
    profiler::proto::BytesResponse* response) {
  response->set_contents(file_cache_.GetFile(request->id())->Contents());
  return Status::OK;
}

}  // namespace profiler
