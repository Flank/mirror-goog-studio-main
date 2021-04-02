/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "android_frame_events_request_handler.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::AndroidFrameEventsResult;
using profiler::perfetto::proto::QueryParameters;

typedef QueryParameters::AndroidFrameEventsParameters
    AndroidFrameEventsParameters;

void AndroidFrameEventsRequestHandler::PopulateFrameEvents(
    AndroidFrameEventsParameters params, AndroidFrameEventsResult* result) {
  // TODO(b/183243964): implmement queries.
}
