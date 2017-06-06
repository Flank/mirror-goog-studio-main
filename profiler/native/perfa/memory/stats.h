
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
#ifndef MEMORY_AGENT_STATS_H
#define MEMORY_AGENT_STATS_H

#include <unistd.h>
#include <atomic>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "utils/log.h"

namespace profiler {

enum MemTag {
  kClassTagMap,
  kClassGlobalRefs,
  kClassData,
  kMethodIds,
  kMemTagCount,
};

extern std::atomic<int64_t> g_max_used[kMemTagCount];
extern std::atomic<int64_t> g_total_used[kMemTagCount];

template <class T>
void atomic_update_max(std::atomic<T>* max, T value) {
  T old_max = max->load();
  while (!max->compare_exchange_weak(old_max, std::max(old_max, value)))
    ;
}

// Minimalist allocator to tracking STL container memoery footprints inside the
// memory agent. Not intended to be used elsewhere.
template <class T, MemTag TAG>
struct TrackingAllocator {
 public:
  typedef T value_type;
  typedef T* pointer;
  typedef const T* const_pointer;
  typedef T& reference;
  typedef const T& const_reference;
  typedef size_t size_type;
  typedef ptrdiff_t difference_type;

  TrackingAllocator() = default;
  TrackingAllocator(const TrackingAllocator& other) {}
  template <class U>
  TrackingAllocator(const TrackingAllocator<U, TAG>& other) {}

  template <class U>
  struct rebind {
    typedef TrackingAllocator<U, TAG> other;
  };

  inline T* allocate(std::size_t n) {
    size_t size = sizeof(T) * n;
    g_total_used[TAG] += size;
    atomic_update_max(&(g_max_used[TAG]), g_total_used[TAG].load());
    return static_cast<T*>(malloc(size));
  };

  inline void deallocate(T* p, std::size_t n) {
    size_t size = sizeof(T) * n;
    g_total_used[TAG] -= size;
    free(p);
  };

  inline void construct(pointer p, const T& val) { new (p) T(val); }
  inline void destroy(pointer p) { p->~T(); }
};

// Auxiliary class for tracking timing data.
class TimingStats {
 public:
  enum TimingTag {
    kAllocate,
    kFree,
    kCallstack,
    kBulkCallstack,
    kLineNumber,
    kTimingTagCount,
  };

  explicit TimingStats()
      : time_(kTimingTagCount),
        max_(kTimingTagCount),
        count_(kTimingTagCount) {}

  inline void Track(TimingTag tag, long time) {
#ifndef NDEBUG
    time_[tag] += time;
    count_[tag]++;
    atomic_update_max(&(max_[tag]), time);
#endif
  }

  void Print(TimingTag tag) {
    long total = time_[tag].load();
    long max = max_[tag].load();
    int count = count_[tag].load();
    Log::V(">> %s: Total=%ld, Count=%d, Max=%ld, Average=%ld", ToString(tag),
           total, count, max, total / count);
  }

 private:
  static const char* ToString(TimingTag tag) {
    switch (tag) {
      case kAllocate:
        return "Allocate";
      case kFree:
        return "Free";
      case kCallstack:
        return "Callstack";
      case kBulkCallstack:
        return "BulkCallstack";
      case kLineNumber:
        return "LineNumber";
      default:
        return "Unknown";
    }
  }

  std::vector<std::atomic<long>> time_;
  std::vector<std::atomic<long>> max_;
  std::vector<std::atomic<int>> count_;
};

namespace tracking {
template <class Key, class T, MemTag TAG, class Hash = std::hash<Key>,
          class Pred = std::equal_to<Key>>
using unordered_map =
    std::unordered_map<Key, T, Hash, Pred,
                       TrackingAllocator<std::pair<const Key, T>, TAG>>;

template <class Key, MemTag TAG, class Hash = std::hash<Key>,
          class KeyEqual = std::equal_to<Key>>
using unordered_set =
    std::unordered_set<Key, Hash, KeyEqual, TrackingAllocator<Key, TAG>>;

template <class T, MemTag TAG>
using vector = std::vector<T, TrackingAllocator<T, TAG>>;
}

}  // namespace profiler

#endif  // MEMORY_AGENT_STATS_H
