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
#include "event_cache.h"

#include <iterator>
#include <vector>

#include "utils/clock.h"
#include "utils/log.h"

using profiler::EventCache;
using profiler::proto::SystemData;
using profiler::proto::ActivityDataResponse;
using profiler::proto::SystemDataResponse;
using profiler::proto::ActivityData;
using profiler::proto::ActivityStateData;
using profiler::proto::ActivityStateData_ActivityState;
using std::lock_guard;

namespace profiler {

void EventCache::AddSystemData(const SystemData& data) {
  lock_guard<std::mutex> lock(system_cache_mutex_);
  if (system_cache_map_.find(data.event_id()) == system_cache_map_.end()) {
    system_cache_map_[data.event_id()] = data;
    // If we are not a touch event ensure we have an end time set so we don't
    // forever return non-touch events.
    if (data.type() != SystemData::TOUCH) {
      system_cache_map_[data.event_id()].set_end_timestamp(
          data.start_timestamp());
    }
  } else {
    system_cache_map_[data.event_id()].set_end_timestamp(
        data.start_timestamp());
  }
}

void EventCache::AddActivityData(const ActivityData& data) {
  lock_guard<std::mutex> lock(activity_cache_mutex_);
  if (activity_cache_map_.find(data.hash()) == activity_cache_map_.end()) {
    activity_cache_map_[data.hash()] = data;
  } else {
    ActivityData& original = activity_cache_map_[data.hash()];
    for (const auto& state : data.state_changes()) {
      ActivityStateData* states = original.add_state_changes();
      states->CopyFrom(state);
    }
  }
}

void EventCache::GetActivityData(int app_id, int64_t start_time,
                                 int64_t end_time,
                                 ActivityDataResponse* response) {
  lock_guard<std::mutex> lock(activity_cache_mutex_);
  for (auto it : activity_cache_map_) {
    ActivityData& data = it.second;
    if (app_id != data.process_id()) {
      continue;
    }

    // We don't do an explicit copy due to manually crafting the activity
    // states.
    ActivityData* out_data = response->add_data();
    out_data->set_name(data.name());
    out_data->set_hash(data.hash());
    out_data->set_process_id(data.process_id());
    out_data->mutable_fragment_data()->CopyFrom(data.fragment_data());

    const auto& states = data.state_changes();
    for (int i = 0; i < states.size(); i++) {
      const auto& state = states.Get(i);
      int64_t timestamp = state.timestamp();
      // Check that the event occurs within the requested time range.
      if (timestamp > start_time && timestamp <= end_time) {
        // Here we return the T-1 result. We only do this in the case we do not
        // already return the first element in the state change list.
        if (out_data->state_changes_size() == 0 && i != 0) {
          ActivityStateData* state_data = out_data->add_state_changes();
          state_data->CopyFrom(states.Get(i - 1));
        }
        ActivityStateData* state_data = out_data->add_state_changes();
        state_data->CopyFrom(state);
      } else if (timestamp > end_time) {
        // Return the T+1 result as the event may extend from before start_time
        // to after end_time.
        ActivityStateData* state_data = out_data->add_state_changes();
        state_data->CopyFrom(states.Get(i));
        break;
      }
    }
    // If no states fit within our time range then we add the last state found
    // for this activity. This ensures that if the state change occured before
    // the requested time and last through the current time we still have an
    // event for this state.
    if (out_data->state_changes_size() == 0) {
      ActivityStateData* state_data = out_data->add_state_changes();
      // Adding the last state from the state list, the state list is guarenteed
      // to have at least one state as an activity is defined by the transition
      // into the CREATED state.
      state_data->CopyFrom(states.Get(states.size() - 1));
    }
  }
}

void EventCache::MarkActivitiesAsTerminated(int process_id) {
  lock_guard<std::mutex> lock(activity_cache_mutex_);
  int64_t current_time = clock_.GetCurrentTime();
  for (auto activity : activity_cache_map_) {
    ActivityData& data = activity.second;
    if (process_id != data.process_id()) {
      continue;
    }

    const auto& states = data.state_changes();
    const int state_size = states.size();
    const auto& state = states.Get(state_size - 1);
    if (state.state() != ActivityStateData::DESTROYED) {
      ActivityStateData* state_data = data.add_state_changes();
      state_data->set_timestamp(current_time);
      state_data->set_state(ActivityStateData::DESTROYED);
      activity_cache_map_[activity.first] = data;
    }
  }
}

void EventCache::GetSystemData(int app_id, int64_t start_time, int64_t end_time,
                               SystemDataResponse* response) {
  lock_guard<std::mutex> lock(system_cache_mutex_);
  for (auto it : system_cache_map_) {
    auto& data = it.second;
    int64_t event_start_time = data.start_timestamp();
    int64_t event_end_time = data.end_timestamp();
    if (app_id != data.process_id()) {
      continue;
    }
    // TODO: Make 0 a const NO_END_TIME meaning the event has not completed.
    if ((start_time < event_end_time || event_end_time == 0) &&
        end_time >= event_start_time) {
      SystemData* out_data = response->add_data();
      out_data->CopyFrom(data);
    }
  }
}

}  // namespace profiler
