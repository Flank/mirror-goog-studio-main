#include "tools/base/deploy/common/utils.h"

#include <sys/types.h>
#include <unistd.h>

#include <sys/syscall.h>

#include "log.h"

namespace {
inline uint64_t GetTime() noexcept {
  struct timespec tp;
  clock_gettime(CLOCK_MONOTONIC_RAW, &tp);
  uint64_t time = ((uint64_t)tp.tv_sec * 1000 * 1000 * 1000) + tp.tv_nsec;
  return time;
}

inline uint64_t gettid() noexcept {
  long tid;
  tid = syscall(SYS_gettid);
  return tid;
}

inline void FillEvent(proto::Event* event,
                      const std::string& message) noexcept {
  event->set_text(message);
  event->set_pid(getpid());
  event->set_tid(gettid());
  event->set_timestamp_ns(GetTime());
}
}  // namespace

void deploy::LogEvent(proto::Event* event, const std::string& message) {
  event->set_type(proto::Event_Type_LOG_OUT);
  Log::I("%s", message.c_str());
  FillEvent(event, message);
}

void deploy::ErrEvent(proto::Event* event, const std::string& message) {
  event->set_type(proto::Event_Type_LOG_ERR);
  Log::E("%s", message.c_str());
  FillEvent(event, message);
}