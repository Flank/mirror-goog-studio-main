/*
 * Copyright (C) 2017 The Android Open Source Project
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
#ifndef PERFD_NETWORK_FAKE_NETWORK_TYPE_PROVIDER_H_
#define PERFD_NETWORK_FAKE_NETWORK_TYPE_PROVIDER_H_

#include "perfd/network/network_type_provider.h"

namespace profiler {

class FakeNetworkTypeProvider final : public NetworkTypeProvider {
 public:
  FakeNetworkTypeProvider(proto::ConnectivityData::NetworkType type)
      : type_(type) {}

  proto::ConnectivityData::NetworkType GetDefaultNetworkType() override {
    return type_;
  }
 private:
  proto::ConnectivityData::NetworkType type_;
};

} // namespace profiler

#endif  // PERFD_NETWORK_FAKE_NETWORK_TYPE_H_
