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

BackgroundQueue::BackgroundQueue(string thread_name)
    : is_task_running_(false), task_thread_name_(thread_name) {
  is_ready_ = true;
  task_thread_ = thread(&BackgroundQueue::TaskThread, this);
}

BackgroundQueue::~BackgroundQueue() {
  is_ready_ = false;
  task_thread_.join();
}

void BackgroundQueue::EnqueueTask(function<void()> task) {
  lock_guard<mutex> lock(task_queue_mutex_);
  task_queue_.push(task);
}

void BackgroundQueue::Reset() {
  lock_guard<mutex> lock(task_queue_mutex_);
  if (!IsRunningUnsafe()) {
    return;
  }

  task_queue_ = queue<function<void()>>();
}

void BackgroundQueue::Join() const {
  while (IsRunning()) {
    std::this_thread::yield();
  }
}

bool BackgroundQueue::IsRunning() const {
  lock_guard<mutex> lock(task_queue_mutex_);
  return IsRunningUnsafe();
}

bool BackgroundQueue::IsRunningUnsafe() const {
  return !task_queue_.empty() || is_task_running_;
}

void BackgroundQueue::TaskThread() {
  SetThreadName(task_thread_name_);

  while (is_ready_) {
    function<void()> task = []() {};

    {
      lock_guard<mutex> lock(task_queue_mutex_);
      if (!task_queue_.empty()) {
        task = task_queue_.front();
        task_queue_.pop();
        is_task_running_ = true;
      }
    }

    task();

    bool is_task_queue_empty;
    {
      lock_guard<mutex> lock(task_queue_mutex_);
      is_task_running_ = false;
      is_task_queue_empty = task_queue_.empty();
    }

    if (is_task_queue_empty) {
      std::this_thread::yield();
    }
  }
}

}  // namespace profiler
