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

#ifndef DEPLOY_EVENT_H
#define DEPLOY_EVENT_H

#include <memory>
#include <string>
#include <vector>

#include "tools/base/deploy/common/trace.h"

namespace deploy {

struct Event {
  uint64_t timestamp_ns;
  enum Type { Logging, Error, Begin, End };
  Type type;
  int64_t pid;
  int64_t tid;
  std::string text;
};

// None of these methods are thread-safe. If we add threading to the native side
// of this project we must change the implementation of the method to protect
// the event storage (e.g: using an std::lock_guard for example).
void InitEventSystem();
void LogEvent(const std::string& text);
void ErrEvent(const std::string& text);
void BeginPhase(const std::string& text);
void EndPhase();
void AddRawEvent(const Event& event);
std::unique_ptr<std::vector<Event>> ConsumeEvents();

// Automatically emit begin/end events (via RAII). Also emit to ftrace.
class Phase {
 public:
  Phase(const std::string& name) : trace_(name) { BeginPhase(name); }
  ~Phase() { EndPhase(); }

 private:
  Trace trace_;
};
}  // namespace deploy

#endif