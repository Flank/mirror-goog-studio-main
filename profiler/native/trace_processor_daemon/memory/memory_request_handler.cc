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

#include "memory_request_handler.h"
#include "absl/strings/escaping.h"

#ifndef _MSC_VER
#include <cxxabi.h>
#endif

using namespace profiler::perfetto;
using perfetto::trace_processor::SqlValue;
using profiler::perfetto::proto::NativeAllocationContext;

long GetLongOrDefault(SqlValue value, long defaultValue) {
  return value.is_null() ? defaultValue : value.long_value;
}

const char* GetStringOrNull(SqlValue value) {
  return value.is_null() ? nullptr : value.string_value;
}

void MemoryRequestHandler::PopulateEvents(NativeAllocationContext* batch) {
  if (batch == nullptr) {
    return;
  }

  auto callsite = processor_->ExecuteQuery(
      "select id, parent_id, frame_id from stack_profile_callsite");
  while (callsite.Next()) {
    auto id = GetLongOrDefault(callsite.Get(0), -1);
    auto parent_id = GetLongOrDefault(callsite.Get(1), -1);
    auto frame_id = GetLongOrDefault(callsite.Get(2), -1);
    auto frame = profiler::perfetto::proto::StackPointer();
    frame.set_parent_id(parent_id);
    frame.set_frame_id(frame_id);
    (*batch->mutable_pointers())[id] = frame;
  }

  auto frames = processor_->ExecuteQuery(
      "select spf.id, spf.name, spm.name, sps.name, sps.source_file, "
      "sps.line_number, sps.id as SymbolId "
      "from stack_profile_frame spf join stack_profile_mapping spm "
      "on spf.mapping = spm.id LEFT join stack_profile_symbol sps on "
      "sps.symbol_set_id = spf.symbol_set_id order by SymbolId asc");
  while (frames.Next()) {
    auto id = frames.Get(0).long_value;
    auto frame_name = GetStringOrNull(frames.Get(1));
    auto module_name = GetStringOrNull(frames.Get(2));
    auto symbol_name = GetStringOrNull(frames.Get(3));
    auto source_file = GetStringOrNull(frames.Get(4));
    auto line_number = GetLongOrDefault(frames.Get(5), 0);
    auto frame = batch->add_frames();
    char* demangled_name = nullptr;
    // TODO (b/151081845): Enable demangling support on windows.
    if (symbol_name != nullptr) {
      frame_name = symbol_name;
    } else if (frame_name != nullptr) {
#ifndef _MSC_VER
      // Demangle stack pointer frame to human readable c++ frame.
      int ignored;
      demangled_name =
          abi::__cxa_demangle(frame_name, nullptr, nullptr, &ignored);
      if (demangled_name != nullptr) {
        frame_name = demangled_name;
      }
#endif
    }
    frame->set_id(id);
    // Due to a bug in utf-8 conversion between java and c++ with proto we
    // encode our strings to base64 and decode them in java.
    // https://github.com/protocolbuffers/protobuf/issues/4691
    if (frame_name != nullptr) {
      std::string converted;
      absl::Base64Escape(frame_name, &converted);
      frame->set_name(converted);
      if (demangled_name != nullptr) {
        delete demangled_name;
      }
    }
    if (module_name != nullptr) {
      std::string converted;
      absl::Base64Escape(module_name, &converted);
      frame->set_module(converted);
    }
    if (source_file != nullptr) {
      std::string converted;
      absl::Base64Escape(source_file, &converted);
      frame->set_source_file(converted);
    }
    frame->set_line_number(line_number);
  }

  // Captures using the "all_heaps" config contain allocations from Art.
  // This heap is currently misleading for developers due to b/183123125.
  auto alloc = processor_->ExecuteQuery(
      "SELECT ts, count, size, callsite_id FROM heap_profile_allocation "
      "WHERE heap_name != 'com.android.art'");
  while (alloc.Next()) {
    auto timestamp = GetLongOrDefault(alloc.Get(0), 0);
    auto count = GetLongOrDefault(alloc.Get(1), 0);
    auto size = GetLongOrDefault(alloc.Get(2), 0);
    auto frame_id = GetLongOrDefault(alloc.Get(3), -1);
    auto allocation = batch->add_allocations();
    allocation->set_timestamp(timestamp);
    allocation->set_size(size);
    allocation->set_count(count);
    allocation->set_stack_id(frame_id);
  }
  // TODO: Query PC Offsets to allow for offline symbolization.
}
