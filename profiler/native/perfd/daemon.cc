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
#include "perfd/daemon.h"

#include <iostream>
#include <memory>

using grpc::Service;
using std::string;

namespace profiler {

void Daemon::RegisterComponent(ProfilerComponent* component) {
  if (component == nullptr) return;
  Service* public_service = component->GetPublicService();
  if (public_service != nullptr) {
    builder_.RegisterService(public_service);
  }
  Service* internal_service = component->GetInternalService();
  if (internal_service != nullptr) {
    builder_.RegisterService(internal_service);
  }
  components_.push_back(component);
}

void Daemon::RunServer(const string& server_address) {
  builder_.AddListeningPort(server_address, grpc::InsecureServerCredentials());
  std::unique_ptr<grpc::Server> server(builder_.BuildAndStart());
  std::cout << "Server listening on " << server_address << std::endl;
  server->Wait();
}

}  // namespace profiler
