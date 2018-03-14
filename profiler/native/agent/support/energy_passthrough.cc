/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <jni.h>
#include <unistd.h>

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"

using grpc::ClientContext;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::SteadyClock;
using profiler::proto::AddEnergyEventRequest;
using profiler::proto::AlarmSet;
using profiler::proto::EmptyEnergyReply;
using profiler::proto::EnergyEvent;
using profiler::proto::InternalEnergyService;
using profiler::proto::JobInfo;
using profiler::proto::JobParameters;
using profiler::proto::JobScheduled;
using profiler::proto::LocationRequest;
using profiler::proto::WakeLockAcquired;
using profiler::proto::WakeLockReleased;

namespace {

// In order to parse wake lock flags we fork constant values from
// https://developer.android.com/reference/android/os/PowerManager.html

// Wake lock levels
constexpr int WAKE_LOCK_LEVEL_MASK = 0x0000ffff;
constexpr int PARTIAL_WAKE_LOCK = 0x00000001;
constexpr int SCREEN_DIM_WAKE_LOCK = 0x00000006;
constexpr int SCREEN_BRIGHT_WAKE_LOCK = 0x0000000a;
constexpr int FULL_WAKE_LOCK = 0x0000001a;
constexpr int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 0x00000020;

// Wake lock flags
constexpr int ACQUIRE_CAUSES_WAKEUP = 0x10000000;
constexpr int ON_AFTER_RELEASE = 0x20000000;

// Wake lock release flags
constexpr int RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY = 0x00000001;

// In order to parse alarm types we fork constant values from
// https://developer.android.com/reference/android/app/AlarmManager.html

// Alarm types
constexpr int RTC = 0x00000001;
constexpr int RTC_WAKEUP = 0x00000000;
constexpr int ELAPSED_REALTIME = 0x00000003;
constexpr int ELAPSED_REALTIME_WAKEUP = 0x00000002;

// In order to parse JobScheduler constants we fork constant values from
// https://developer.android.com/reference/android/app/job/JobScheduler.html

// Job schedule result
constexpr int RESULT_FAILURE = 0x00000000;
constexpr int RESULT_SUCCESS = 0x00000001;

// Job backoff policy
constexpr int BACKOFF_POLICY_LINEAR = 0x00000000;
constexpr int BACKOFF_POLICY_EXPONENTIAL = 0x00000001;

// Job network type
constexpr int NETWORK_TYPE_NONE = 0x00000000;
constexpr int NETWORK_TYPE_ANY = 0x00000001;
constexpr int NETWORK_TYPE_UNMETERED = 0x00000002;
constexpr int NETWORK_TYPE_NOT_ROAMING = 0x00000003;
constexpr int NETWORK_TYPE_METERED = 0x00000004;

// Location accuracy
constexpr int ACCURACY_FINE = 0x00000001;
constexpr int ACCURACY_COARSE = 0x00000002;

// Location power requirement
constexpr int POWER_LOW = 0x00000001;
constexpr int POWER_HIGH = 0x00000003;

// Location provider
constexpr char GPS_PROVIDER[] = "gps";
constexpr char NETWORK_PROVIDER[] = "network";
constexpr char PASSIVE_PROVIDER[] = "passive";
constexpr char FUSED_PROVIDER[] = "fused";

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

// Enqueue and submit the target |energy_event|. The event's timestamp will be
// set as a side-effect of calling this method, but all other fields and
// appropriate metadata must be set by the caller.
void SubmitEnergyEvent(const EnergyEvent& energy_event,
                       const std::string& stack = {}) {
  int64_t timestamp = GetClock().GetCurrentTime();
  Agent::Instance().SubmitEnergyTasks(
      {[energy_event, stack, timestamp](InternalEnergyService::Stub& stub,
                                        ClientContext& ctx) {
        AddEnergyEventRequest request;
        request.mutable_energy_event()->CopyFrom(energy_event);
        request.mutable_energy_event()->set_timestamp(timestamp);
        request.set_callstack(stack);

        EmptyEnergyReply response;
        return stub.AddEnergyEvent(&ctx, request, &response);
      }});
}

AlarmSet::Type ParseAlarmType(jint type) {
  switch (type) {
    case RTC:
      return AlarmSet::RTC;
    case RTC_WAKEUP:
      return AlarmSet::RTC_WAKEUP;
    case ELAPSED_REALTIME:
      return AlarmSet::ELAPSED_REALTIME;
    case ELAPSED_REALTIME_WAKEUP:
      return AlarmSet::ELAPSED_REALTIME_WAKEUP;
    default:
      return AlarmSet::UNDEFINED_ALARM_TYPE;
  }
}

void PopulateJobParams(JNIEnv* env, JobParameters* params, jint job_id,
                       jobjectArray triggered_content_authorities,
                       jobjectArray triggered_content_uris,
                       jboolean is_override_deadline_expired, jstring extras,
                       jstring transient_extras) {
  JStringWrapper extras_str(env, extras);
  JStringWrapper transient_extras_str(env, transient_extras);
  params->set_job_id(job_id);

  if (triggered_content_authorities != nullptr) {
    jsize len = env->GetArrayLength(triggered_content_authorities);
    for (jsize i = 0; i < len; ++i) {
      jstring authority =
          (jstring)env->GetObjectArrayElement(triggered_content_authorities, i);
      JStringWrapper authority_str(env, authority);
      params->add_triggered_content_authorities(authority_str.get());
      env->DeleteLocalRef(authority);
    }
  }

  if (triggered_content_uris != nullptr) {
    jsize len = env->GetArrayLength(triggered_content_uris);
    for (jsize i = 0; i < len; ++i) {
      jstring uri =
          (jstring)env->GetObjectArrayElement(triggered_content_uris, i);
      JStringWrapper uri_str(env, uri);
      params->add_triggered_content_uris(uri_str.get());
      env->DeleteLocalRef(uri);
    }
  }

  params->set_is_override_deadline_expired(is_override_deadline_expired);
  params->set_extras(extras_str.get());
  params->set_transient_extras(transient_extras_str.get());
}

LocationRequest::Priority GetPriority(jint accuracy, jint power_req,
                                      const std::string& provider) {
  switch (accuracy) {
    case ACCURACY_FINE:
      return LocationRequest::HIGH_ACCURACY;
    case ACCURACY_COARSE:
      return LocationRequest::BALANCED;
    default: {
      switch (power_req) {
        case POWER_LOW:
          return LocationRequest::LOW_POWER;
        case POWER_HIGH:
          return LocationRequest::HIGH_ACCURACY;
        default: {
          if (strcmp(provider.c_str(), GPS_PROVIDER) == 0) {
            return LocationRequest::HIGH_ACCURACY;
          }
          if (strcmp(provider.c_str(), PASSIVE_PROVIDER) == 0) {
            return LocationRequest::NO_POWER;
          }
          return LocationRequest::LOW_POWER;
        }
      }
    }
  }
}
}  // namespace

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockAcquired(
    JNIEnv* env, jclass clazz, jint event_id, jint flags, jstring tag,
    jlong timeout, jstring stack) {
  JStringWrapper tag_string(env, tag);
  JStringWrapper stack_string(env, stack);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto wake_lock_acquired = energy_event.mutable_wake_lock_acquired();
  WakeLockAcquired::Level level;
  switch (flags & WAKE_LOCK_LEVEL_MASK) {
    case PARTIAL_WAKE_LOCK:
      level = WakeLockAcquired::PARTIAL_WAKE_LOCK;
      break;
    case SCREEN_DIM_WAKE_LOCK:
      level = WakeLockAcquired::SCREEN_DIM_WAKE_LOCK;
      break;
    case SCREEN_BRIGHT_WAKE_LOCK:
      level = WakeLockAcquired::SCREEN_BRIGHT_WAKE_LOCK;
      break;
    case FULL_WAKE_LOCK:
      level = WakeLockAcquired::FULL_WAKE_LOCK;
      break;
    case PROXIMITY_SCREEN_OFF_WAKE_LOCK:
      level = WakeLockAcquired::PROXIMITY_SCREEN_OFF_WAKE_LOCK;
      break;
    default:
      level = WakeLockAcquired::UNDEFINED_WAKE_LOCK_LEVEL;
      break;
  }
  wake_lock_acquired->set_level(level);
  if ((flags & ACQUIRE_CAUSES_WAKEUP) != 0) {
    wake_lock_acquired->mutable_flags()->Add(
        WakeLockAcquired::ACQUIRE_CAUSES_WAKEUP);
  }
  if ((flags & ON_AFTER_RELEASE) != 0) {
    wake_lock_acquired->mutable_flags()->Add(
        WakeLockAcquired::ON_AFTER_RELEASE);
  }
  wake_lock_acquired->set_tag(tag_string.get());
  wake_lock_acquired->set_timeout(timeout);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockReleased(
    JNIEnv* env, jclass clazz, jint event_id, jint flags, jboolean is_held,
    jstring stack) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  if ((flags & RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) != 0) {
    energy_event.mutable_wake_lock_released()->mutable_flags()->Add(
        WakeLockReleased::RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
  }
  energy_event.mutable_wake_lock_released()->set_is_held(is_held);
  energy_event.set_is_terminal(!is_held);
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendIntentAlarmScheduled(
    JNIEnv* env, jclass clazz, jint event_id, jint type, jlong trigger_ms,
    jlong window_ms, jlong interval_ms, jstring creator_package,
    jint creator_uid, jstring stack) {
  JStringWrapper creator_package_str(env, creator_package);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto alarm_set = energy_event.mutable_alarm_set();
  alarm_set->set_type(ParseAlarmType(type));
  alarm_set->set_trigger_ms(trigger_ms);
  alarm_set->set_window_ms(window_ms);
  alarm_set->set_interval_ms(interval_ms);
  alarm_set->mutable_operation()->set_creator_package(
      creator_package_str.get());
  alarm_set->mutable_operation()->set_creator_uid(creator_uid);
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendListenerAlarmScheduled(
    JNIEnv* env, jclass clazz, jint event_id, jint type, jlong trigger_ms,
    jlong window_ms, jlong interval_ms, jstring listener_tag, jstring stack) {
  JStringWrapper listener_tag_str(env, listener_tag);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto alarm_set = energy_event.mutable_alarm_set();
  alarm_set->set_type(ParseAlarmType(type));
  alarm_set->set_trigger_ms(trigger_ms);
  alarm_set->set_window_ms(window_ms);
  alarm_set->set_interval_ms(interval_ms);
  alarm_set->mutable_listener()->set_tag(listener_tag_str.get());
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendIntentAlarmCancelled(
    JNIEnv* env, jclass clazz, jint event_id, jstring creator_package,
    jint creator_uid, jstring stack) {
  JStringWrapper creator_package_str(env, creator_package);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto alarm_cancelled = energy_event.mutable_alarm_cancelled();
  alarm_cancelled->mutable_operation()->set_creator_package(
      creator_package_str.get());
  alarm_cancelled->mutable_operation()->set_creator_uid(creator_uid);
  energy_event.set_is_terminal(true);
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendListenerAlarmCancelled(
    JNIEnv* env, jclass clazz, jint event_id, jstring listener_tag,
    jstring stack) {
  JStringWrapper listener_tag_str(env, listener_tag);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  energy_event.mutable_alarm_cancelled()->mutable_listener()->set_tag(
      listener_tag_str.get());
  energy_event.set_is_terminal(true);
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendListenerAlarmFired(
    JNIEnv* env, jclass clazz, jint event_id, jstring listener_tag) {
  JStringWrapper listener_tag_str(env, listener_tag);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  energy_event.mutable_alarm_fired()->mutable_listener()->set_tag(
      listener_tag_str.get());
  // Listener alarm cannot repeat so it's always terminal.
  energy_event.set_is_terminal(true);
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_JobWrapper_sendJobScheduled(
    JNIEnv* env, jclass clazz, jint event_id, jint job_id, jstring service_name,
    jint backoff_policy, jlong initial_backoff_ms, jboolean is_periodic,
    jlong flex_ms, jlong interval_ms, jlong min_latency_ms,
    jlong max_execution_delay_ms, jint network_type,
    jobjectArray trigger_content_uris, jlong trigger_content_max_delay,
    jlong trigger_content_update_delay, jboolean is_persisted,
    jboolean is_require_battery_not_low, jboolean is_require_charging,
    jboolean is_require_device_idle, jboolean is_require_storage_not_low,
    jstring extras, jstring transient_extras, jint schedule_result,
    jstring stack) {
  JStringWrapper service_name_str(env, service_name);
  JStringWrapper extras_str(env, extras);
  JStringWrapper transient_extras_str(env, transient_extras);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);

  auto job = energy_event.mutable_job_scheduled()->mutable_job();
  job->set_job_id(job_id);
  job->set_service_name(service_name_str.get());
  job->set_initial_backoff_ms(initial_backoff_ms);
  job->set_is_periodic(is_periodic);
  job->set_flex_ms(flex_ms);
  job->set_interval_ms(interval_ms);
  job->set_min_latency_ms(min_latency_ms);
  job->set_max_execution_delay_ms(max_execution_delay_ms);
  job->set_trigger_content_max_delay(trigger_content_max_delay);
  job->set_trigger_content_update_delay(trigger_content_update_delay);
  job->set_is_persisted(is_persisted);
  job->set_is_require_battery_not_low(is_require_battery_not_low);
  job->set_is_require_charging(is_require_charging);
  job->set_is_require_device_idle(is_require_device_idle);
  job->set_is_require_storage_not_low(is_require_storage_not_low);
  job->set_extras(extras_str.get());
  job->set_transient_extras(transient_extras_str.get());

  JobInfo::BackoffPolicy backoff_policy_enum;
  switch (backoff_policy) {
    case BACKOFF_POLICY_LINEAR:
      backoff_policy_enum = JobInfo::BACKOFF_POLICY_LINEAR;
      break;
    case BACKOFF_POLICY_EXPONENTIAL:
      backoff_policy_enum = JobInfo::BACKOFF_POLICY_EXPONENTIAL;
      break;
    default:
      backoff_policy_enum = JobInfo::UNDEFINED_BACKOFF_POLICY;
      break;
  }
  job->set_backoff_policy(backoff_policy_enum);

  JobInfo::NetworkType network_type_enum;
  switch (network_type) {
    case NETWORK_TYPE_NONE:
      network_type_enum = JobInfo::NETWORK_TYPE_NONE;
      break;
    case NETWORK_TYPE_ANY:
      network_type_enum = JobInfo::NETWORK_TYPE_ANY;
      break;
    case NETWORK_TYPE_UNMETERED:
      network_type_enum = JobInfo::NETWORK_TYPE_UNMETERED;
      break;
    case NETWORK_TYPE_NOT_ROAMING:
      network_type_enum = JobInfo::NETWORK_TYPE_NOT_ROAMING;
      break;
    case NETWORK_TYPE_METERED:
      network_type_enum = JobInfo::NETWORK_TYPE_METERED;
      break;
    default:
      network_type_enum = JobInfo::UNDEFINED_NETWORK_TYPE;
      break;
  }
  job->set_network_type(network_type_enum);

  jsize len = env->GetArrayLength(trigger_content_uris);
  for (jsize i = 0; i < len; ++i) {
    jstring uri = (jstring)env->GetObjectArrayElement(trigger_content_uris, i);
    JStringWrapper uri_str(env, uri);
    job->add_trigger_content_uris(uri_str.get());
    env->DeleteLocalRef(uri);
  }

  JobScheduled::Result result;
  switch (schedule_result) {
    case RESULT_FAILURE:
      result = JobScheduled::RESULT_FAILURE;
      break;
    case RESULT_SUCCESS:
      result = JobScheduled::RESULT_SUCCESS;
      break;
    default:
      result = JobScheduled::UNDEFINED_JOB_SCHEDULE_RESULT;
      break;
  }
  energy_event.mutable_job_scheduled()->set_result(result);
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_JobWrapper_sendJobStarted(
    JNIEnv* env, jclass clazz, jint event_id, jint job_id,
    jobjectArray triggered_content_authorities,
    jobjectArray triggered_content_uris, jboolean is_override_deadline_expired,
    jstring extras, jstring transient_extras, jboolean work_ongoing) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto params = energy_event.mutable_job_started()->mutable_params();
  PopulateJobParams(env, params, job_id, triggered_content_authorities,
                    triggered_content_uris, is_override_deadline_expired,
                    extras, transient_extras);
  energy_event.mutable_job_started()->set_work_ongoing(work_ongoing);
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_JobWrapper_sendJobStopped(
    JNIEnv* env, jclass clazz, jint event_id, jint job_id,
    jobjectArray triggered_content_authorities,
    jobjectArray triggered_content_uris, jboolean is_override_deadline_expired,
    jstring extras, jstring transient_extras, jboolean reschedule) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto params = energy_event.mutable_job_stopped()->mutable_params();
  PopulateJobParams(env, params, job_id, triggered_content_authorities,
                    triggered_content_uris, is_override_deadline_expired,
                    extras, transient_extras);
  energy_event.mutable_job_stopped()->set_reschedule(reschedule);
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_JobWrapper_sendJobFinished(
    JNIEnv* env, jclass clazz, jint event_id, jint job_id,
    jobjectArray triggered_content_authorities,
    jobjectArray triggered_content_uris, jboolean is_override_deadline_expired,
    jstring extras, jstring transient_extras, jboolean needs_reschedule,
    jstring stack) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto params = energy_event.mutable_job_finished()->mutable_params();
  PopulateJobParams(env, params, job_id, triggered_content_authorities,
                    triggered_content_uris, is_override_deadline_expired,
                    extras, transient_extras);
  energy_event.mutable_job_finished()->set_needs_reschedule(needs_reschedule);
  energy_event.set_is_terminal(true);
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_LocationManagerWrapper_sendListenerLocationUpdateRequested(
    JNIEnv* env, jclass clazz, jint event_id, jstring provider, jlong interval,
    jfloat min_distance, jint accuracy, jint power_req) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  energy_event.mutable_location_update_requested()->mutable_listener();
  auto request =
      energy_event.mutable_location_update_requested()->mutable_request();
  JStringWrapper provider_str(env, provider);
  request->set_provider(provider_str.get());
  request->set_interval_ms(interval);
  request->set_fastest_interval_ms(interval);
  request->set_smallest_displacement_meters(min_distance);
  request->set_priority(GetPriority(accuracy, power_req, provider_str.get()));
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_LocationManagerWrapper_sendIntentLocationUpdateRequested(
    JNIEnv* env, jclass clazz, jint event_id, jstring provider, jlong interval,
    jfloat min_distance, jint accuracy, jint power_req, jstring creator_package,
    jint creator_uid) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto intent =
      energy_event.mutable_location_update_requested()->mutable_intent();
  JStringWrapper creator_package_str(env, creator_package);
  intent->set_creator_package(creator_package_str.get());
  intent->set_creator_uid(creator_uid);
  auto request =
      energy_event.mutable_location_update_requested()->mutable_request();
  JStringWrapper provider_str(env, provider);
  request->set_provider(provider_str.get());
  request->set_interval_ms(interval);
  request->set_fastest_interval_ms(interval);
  request->set_smallest_displacement_meters(min_distance);
  request->set_priority(GetPriority(accuracy, power_req, provider_str.get()));
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_LocationManagerWrapper_sendListenerLocationUpdateRemoved(
    JNIEnv* env, jclass clazz, jint event_id) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  energy_event.set_is_terminal(true);
  energy_event.mutable_location_update_removed()->mutable_listener();
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_LocationManagerWrapper_sendIntentLocationUpdateRemoved(
    JNIEnv* env, jclass clazz, jint event_id, jstring creator_package,
    jint creator_uid) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  energy_event.set_is_terminal(true);
  auto intent =
      energy_event.mutable_location_update_removed()->mutable_intent();
  JStringWrapper creator_package_str(env, creator_package);
  intent->set_creator_package(creator_package_str.get());
  intent->set_creator_uid(creator_uid);
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_LocationManagerWrapper_sendListenerLocationChanged(
    JNIEnv* env, jclass clazz, jint event_id) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  energy_event.mutable_location_changed()->mutable_listener();
  SubmitEnergyEvent(energy_event);
}
};
