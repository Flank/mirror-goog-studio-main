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
#include "background_queue.h"

#include <unistd.h>

#include "utils/clock.h"
#include "utils/thread_name.h"

using profiler::Clock;
using std::function;
using std::lock_guard;
using std::mutex;
using std::thread;
using std::string;
using std::queue;

namespace profiler {

BackgroundQueue::BackgroundQueue(string thread_name, int max_length)
    : max_length_(max_length),
      is_task_running_(false),
      task_thread_name_(thread_name) {
  assert(max_length_ > 0 || max_length_ == -1);
  task_thread_ = thread(&BackgroundQueue::TaskThread, this);
}

BackgroundQueue::~BackgroundQueue() {
  task_channel_.Finish();
  task_thread_.join();
}

void BackgroundQueue::EnqueueTask(function<void()> task) {
  if (max_length_ > 0 &&
      task_channel_.length() == static_cast<size_t>(max_length_)) {
    // If we're falling behind (most likely because the background tasks are
    // blocked), then just drop the oldest to make way for the newest.
    function<void()> oldest_task;
    task_channel_.Pop(&oldest_task);
  }

  task_channel_.Push(task);
}

bool BackgroundQueue::IsIdle() const {
  return task_channel_.length() == 0 && !is_task_running_;
}

void BackgroundQueue::TaskThread() {
  SetThreadName(task_thread_name_);

  function<void()> task;
  while (task_channel_.Pop(&task)) {
    is_task_running_ = true;
    task();
    is_task_running_ = false;
  }
}

}  // namespace profiler
