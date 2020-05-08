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

#include "tools/base/deploy/common/utils.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

deploy::Event ConvertProtoEventToEvent(
    const proto::Event& proto_event) noexcept {
  Event event;
  event.tid = proto_event.tid();
  event.pid = proto_event.pid();
  event.text = proto_event.text();
  event.timestamp_ns = proto_event.timestamp_ns();
  deploy::Event::Type type;
  switch (proto_event.type()) {
    case proto::Event::LOG_ERR:
      type = Event::Type::Error;
      break;
    case proto::Event::LOG_OUT:
      type = Event::Type::Logging;
      break;
    case proto::Event::TRC_BEG:
      type = Event::Type::Begin;
      break;
    case proto::Event::TRC_METRIC:
      type = Event::Type::BeginMetric;
      break;
    case proto::Event::TRC_END:
      type = Event::Type::End;
      break;
  }
  event.type = type;
  return event;
}

void ConvertEventToProtoEvent(deploy::Event& event,
                              proto::Event* proto_event) noexcept {
  proto::Event_Type proto_type;
  switch (event.type) {
    case deploy::Event::Type::Begin:
      proto_type = proto::Event_Type_TRC_BEG;
      break;
    case deploy::Event::Type::BeginMetric:
      proto_type = proto::Event_Type_TRC_METRIC;
      break;
    case deploy::Event::Type::End:
      proto_type = proto::Event_Type_TRC_END;
      break;
    case deploy::Event::Type::Error:
      proto_type = proto::Event_Type::Event_Type_LOG_ERR;
      break;
    case deploy::Event::Type::Logging:
      proto_type = proto::Event_Type::Event_Type_LOG_OUT;
      break;
  }
  proto_event->set_type(proto_type);
  proto_event->set_text(event.text);
  proto_event->set_pid(event.pid);
  proto_event->set_tid(event.tid);
  proto_event->set_timestamp_ns(event.timestamp_ns);
}

bool ReadFile(const std::string& file_path, std::string* content) {
  int fd = open(file_path.c_str(), O_RDONLY);
  if (fd == -1) {
    ErrEvent("Could not open file at '" + file_path + "': " + strerror(errno));
    return false;
  }

  struct stat st;
  if (fstat(fd, &st) != 0) {
    ErrEvent("Could not stat file at '" + file_path + "': " + strerror(errno));
    return false;
  }

  content->resize(st.st_size);

  ssize_t len;
  size_t bytes_read = 0;
  while ((len = read(fd, &(*content)[0] + bytes_read,
                     st.st_size - bytes_read)) > 0) {
    bytes_read += len;
  }
  close(fd);

  if (bytes_read < st.st_size) {
    ErrEvent("Could not read file at '" + file_path + "'");
    return false;
  }

  return true;
}

bool WriteFile(const std::string& file_path, const std::string& content) {
  int fd = creat(file_path.c_str(), S_IRWXU);
  if (fd == -1) {
    ErrEvent("Could not create file at '" + file_path +
             "': " + strerror(errno));
    return false;
  }

  size_t count = 0;
  while (count < content.size()) {
    ssize_t len = write(fd, content.data() + count, content.size() - count);
    if (len < 0) {
      ErrEvent("Could not write to file at '" + file_path +
               "': " + strerror(errno));
      break;
    }
    count += len;
  }

  close(fd);
  if (count < content.size()) {
    ErrEvent("Failed to write all bytes of file '" + file_path + "'");
    return false;
  }

  return true;
}

std::string GetAgentExceptionLogDir(const std::string& package_name) {
  std::ostringstream log_dir;
  log_dir << Env::root() << "/data/data/" << package_name << "/.agent-logs";
  return log_dir.str();
}

}  // namespace deploy
