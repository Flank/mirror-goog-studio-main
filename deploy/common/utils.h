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

#ifndef DEPLOY_UTILS_H
#define DEPLOY_UTILS_H

#include <fcntl.h>
#include <stddef.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <sstream>
#include <string>
#include <type_traits>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace {

template <typename T>
using is_string = std::is_same<T, std::string>;

template <typename T>
struct is_vector : public std::false_type {};

template <typename T>
struct is_vector<std::vector<T>> : public std::true_type {};

}  // namespace

namespace deploy {

extern const std::string kAgent;
extern const std::string kAgentAlt;
extern const std::string kInstallServer;

// std::literals::string_literals::operator""s is only available in c++14. Until
// we switch NDK to move up from c++11, this is our own syntax sugar via user
// defined literal.
inline const std::string operator"" _s(const char* c, std::size_t size) {
  return std::string(c, size);
}

void ConvertProtoEventsToEvents(
    const google::protobuf::RepeatedPtrField<proto::Event>& events);

deploy::Event ConvertProtoEventToEvent(
    const proto::Event& proto_event) noexcept;

void ConvertEventToProtoEvent(deploy::Event& event,
                              proto::Event* proto_event) noexcept;

// std::to_string is not available in the current NDK.
// TODO: Delete this when we update to a newer version.
template <typename T>
std::string to_string(const T& n) {
  std::ostringstream stm;
  stm << n;
  return stm.str();
}

// Reads a file from the specified path with to the specified container.
//
// This method currently only supports reading to strings or vectors.
template <typename T>
bool ReadFile(const std::string& file_path, T* content) {
  static_assert(
      is_string<T>::value || is_vector<T>::value,
      "Template parameter 'T' must be of type std::vector or std::string");
  int fd = IO::open(file_path, O_RDONLY);
  if (fd == -1) {
    LogEvent("Could not open file at '" + file_path + "': " + strerror(errno));
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

// Writes a file to the specified path with the specified content. Overwrites
// any existing file at that path.
//
// This method currently only supports writing strings or vectors.
template <typename T>
bool WriteFile(const std::string& file_path, const T& content) {
  static_assert(
      is_string<T>::value || is_vector<T>::value,
      "Template parameter 'T' must have type std::vector or std::string");
  int fd = IO::creat(file_path, S_IRWXU);
  if (fd == -1) {
    ErrEvent("Could not create file at '" + file_path +
             "': " + strerror(errno));
    return false;
  }

  if (flock(fd, LOCK_EX)) {
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

  // This also releases the advisory lock on the file.
  close(fd);
  if (count < content.size()) {
    ErrEvent("Failed to write all bytes of file '" + file_path + "'");
    return false;
  }

  return true;
}
}  // namespace deploy
#endif