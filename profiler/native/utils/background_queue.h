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
#ifndef PERFA_BACKGROUND_QUEUE_H
#define PERFA_BACKGROUND_QUEUE_H

#include <atomic>
#include <functional>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>

namespace profiler {

// A thread-safe queue of tasks which will be run sequentially on a background
// thread. The queue can also be reset, which will clear it and remove any
// enqueued tasks that haven't run yet.
//
// Example:
//   BackgroundQueue bq("LongTasks");
//   bq.EnqueueTask([]() { ... long operation #1 ... })
//   bq.EnqueueTask([]() { ... long operation #2 ... })
//   bq.Join(); // Blocks until all operations are finished
class BackgroundQueue {
 public:
  // TODO: Handle a case where infinite items can be added to the queue, e.g.
  // when perfa can't reach perfd. Add max queue length? And if the max is hit,
  // should we enqueue new tasks dropping oldest, or ignore the enqueue request
  // instead?
  explicit BackgroundQueue(std::string thread_name);
  ~BackgroundQueue();

  // Add a task to the end of the queue. It will automatically be run after all
  // prior tasks finish; in other words, tasks are not run simultaneously.
  void EnqueueTask(std::function<void()> task);

  // Remove any tasks still on this queue
  void Reset();

  // Blocks the current thread until all background tasks are complete
  void Join() const;

  // Whether any background tasks are running right now.
  bool IsRunning() const;

 private:
  // The logic for whether this queue is currently running, but callers are
  // responsible for locking against |task_queue_mutex_| before calling.
  bool IsRunningUnsafe() const;

  // The background method responsible for pulling the next task out of the
  // queue and running it.
  void TaskThread();

  std::queue<std::function<void()>> task_queue_;
  bool is_task_running_;
  mutable std::mutex task_queue_mutex_;

  std::thread task_thread_;
  std::string task_thread_name_;
  std::atomic_bool is_ready_;
};

}  // namespace profiler

#endif  // PERFA_BACKGROUND_QUEUE_H
