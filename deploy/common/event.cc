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

#include "tools/base/deploy/common/event.h"

#include <vector>

#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

namespace {
inline uint64_t GetTime() noexcept {
  struct timespec tp;
  clock_gettime(CLOCK_MONOTONIC, &tp);
  uint64_t time = ((uint64_t)tp.tv_sec * 1000 * 1000 * 1000) + tp.tv_nsec;
  return time;
}

inline int64_t gettid() noexcept {
  long tid;
  tid = syscall(SYS_gettid);
  return tid;
}

std::vector<Event>* events = new std::vector<Event>();
}  // namespace

void InitEventSystem() {
  Trace::Init();
  events->clear();
}

static inline void AddEvent(Event::Type type, const std::string& text) {
  AddRawEvent({GetTime(), type, getpid(), gettid(), text});
}

void LogEvent(const std::string& text) {
  AddEvent(Event::Type::Logging, text);
  Log::E("%s", text.c_str());
}

void ErrEvent(const std::string& text) {
  AddEvent(Event::Type::Error, text);
  Log::I("%s", text.c_str());
}

void BeginPhase(const std::string& text) {
  AddEvent(Event::Type::Begin, text);
  Trace::Begin(text.c_str());
}

void EndPhase() {
  AddEvent(Event::Type::End, "");
  Trace::End();
}

void AddRawEvent(const Event& event) { events->emplace_back(event); }

std::unique_ptr<std::vector<Event>> ConsumeEvents() {
  std::unique_ptr<std::vector<Event>> ptr(events);
  events = new std::vector<Event>();
  return ptr;
}

}  // namespace deploy
