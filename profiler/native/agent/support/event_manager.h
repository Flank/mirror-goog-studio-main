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
#ifndef EVENT_INITIALIZER_H_
#define EVENT_INITIALIZER_H_

#include <mutex>

#include "agent/support/jni_wrappers.h"
#include "proto/internal_event.grpc.pb.h"

namespace profiler {

class EventManager {
 public:
  // Grab the singleton instance of initializer. This will initialize the class
  // if necessary.
  static EventManager& Instance();

  // Cache the raw component data, also enqueue the event to send to perfd.
  // We cache off the last component event sent, and resend it when we reset
  // connection to perfd. If we don't do this, the component manifest it self as
  // not starting when reconnecting to perfd with a cleared cache.
  void CacheAndEnqueueActivityEvent(
      const profiler::proto::ActivityData& activity);

 private:
  explicit EventManager();

  // Helper funciton to enque event without caching the values first.
  void EnqueueActivityEvent(const profiler::proto::ActivityData& activity);

  // Function callback to listen to perfd state chagnes, this happens on the
  // heartbeat thread. This thread is not the thread that
  // CacheAndEnqueuecomponentEvent is run on.
  void PerfdStateChanged(bool becomes_alive);

  // Cached values of active activities. The key is the
  // unique hash of the component, the value being a copy of the
  // component data needed by perfd upon connection.
  std::map<int, profiler::proto::ActivityData> hash_activity_cache_;

  // Mutex guards us from attempting to send a state change, the exact same
  // moment we change the perfd state to online.
  std::mutex activity_cache_mutex_;
};

}  // end of namespace profiler

#endif  // EVENT_INITIALIZER_H_
