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
#ifndef PERFD_NETWORK_CONNECTION_DETAILS_H_
#define PERFD_NETWORK_CONNECTION_DETAILS_H_

#include <string>

namespace profiler {

// Various metadata associated with an HTTP connection. Note that various fields
// in this structure will be unintialized until available, as a connection's
// data is populated over its lifecycle. Comments are included to indicate when
// data is expected to be available.
// TODO: Many fields are stubs to be populated shortly. Included early to help
// clarify the design of this class.
struct ConnectionDetails final {
  struct Request {
    // The full URL path of this connection.
    // Available immediately.
    std::string url;

    // The HTTP request method (GET, UPDATE, POST, etc.)
    // Available immediately.
    std::string method;

    // Key/value pairs sent with this request
    // Available immediately.
    std::string fields;

    // The code stacktrace where this connection was created
    // Available immediately.
    std::string trace;

    // A unique ID which can be used as a key to fetch the contents of a request
    // payload (or empty string otherwise).
    // Available when |downloading_timestamp| is non-zero.
    std::string payload_id;  // TODO: Populate this
  };

  // TODO: It seems like all Response data is available when |end_timestamp| is
  // non-zero. Confirm this once we have real data, and if so, simplify the
  // docs.
  struct Response {
    // The HTTP response status code (200, 404, etc.)
    // Available when |end_timestamp| is non-zero.
    std::string code;  // TODO: Populate this

    // Key/value pairs sent with this response
    // Available when |end_timestamp| is non-zero.
    std::string fields;

    // A unique ID which can be used as a key to fetch the contents of a
    // response payload (or empty string otherwise).
    // Available when |end_timestamp| is non-zero.
    std::string payload_id;
  };

  // Thread information obtained from Java, which is different from the thread data we would obtain from a JNI context.
  // See also: https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html
  struct JavaThread {
    // ID of the thread obtained from Java, which is different from the thread ID obtained in a JNI context.
    int64_t id;
    // Name of the thread.
    // https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#getName().
    std::string name;

    JavaThread(int64_t id, std::string name) : id(id), name(name) {}
  };

  // ID that can identify this connection globally across all active apps
  int64_t id = 0;
  // The ID of the app that created this connection
  int32_t app_id = 0;
  // Time when this connection was created. This should always be set.
  int64_t start_timestamp = 0;
  // Time when the server responded back with the first byte (and downloading
  // the complete response has begun). This value will be 0 until then.
  int64_t downloading_timestamp = 0;
  // Time when the connection was closed (either completed or aborted). This
  // value will be 0 until then.
  int64_t end_timestamp = 0;
  // The threads where this connection was accessed.
  // At least one thread will always exist, and the first thread will be the one in which this connection was created.
  std::vector<JavaThread> threads;
  Request request;
  Response response;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_CONNECTION_DETAILS_H_
