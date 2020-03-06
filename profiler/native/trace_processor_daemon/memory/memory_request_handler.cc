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

#ifndef _MSC_VER
#include <cxxabi.h>
#endif

using namespace profiler::perfetto;
using profiler::perfetto::proto::NativeAllocationContext;

void MemoryRequestHandler::PopulateEvents(NativeAllocationContext* batch) {
  if (batch == nullptr) {
    return;
  }

  auto callsite = processor_->ExecuteQuery(
      "select id, parent_id, frame_id from stack_profile_callsite");
  while (callsite.Next()) {
    auto id = callsite.Get(0).long_value;
    auto parent_id = callsite.Get(1).long_value;
    auto frame_id = callsite.Get(2).long_value;
    auto pointer = batch->add_pointers();
    pointer->set_id(id);
    pointer->set_parent_id(parent_id);
    pointer->set_frame_id(frame_id);
  }

  auto frames = processor_->ExecuteQuery(
      "select spf.id, spf.name, spm.name, spf.rel_pc "
      "from stack_profile_frame spf join stack_profile_mapping spm "
      "on spf.mapping = spm.id");
  while (frames.Next()) {
    auto id = frames.Get(0).long_value;
    auto frame_name_field = frames.Get(1);
    auto frame_name =
        frame_name_field.is_null() ? "" : frame_name_field.string_value;
    auto module_name_field = frames.Get(2);
    auto module_name =
        module_name_field.is_null() ? "" : module_name_field.string_value;
    auto rel_pc = frames.Get(3).long_value;
    auto frame = batch->add_frames();
    // TODO (b/151081845): Enable demangling support on windows.
#ifdef _MSC_VER
    frame->set_name(frame_name);
#else
    // Demangle stack pointer frame to human readable c++ frame.
    int ignored;
    char* data = abi::__cxa_demangle(frame_name, nullptr, nullptr, &ignored);
    if (data != nullptr) {
      frame->set_name(data);
    } else {
      frame->set_name(frame_name);
    }
#endif
    frame->set_id(id);
    frame->set_module(module_name);
    frame->set_rel_pc(rel_pc);
  }

  auto alloc = processor_->ExecuteQuery(
      "select ts, count, size, callsite_id from heap_profile_allocation");
  while (alloc.Next()) {
    auto timestamp = alloc.Get(0).long_value;
    auto count = alloc.Get(1).long_value;
    auto size = alloc.Get(2).long_value;
    auto frame_id = alloc.Get(3).long_value;
    auto allocation = batch->add_allocations();
    allocation->set_timestamp(timestamp);
    allocation->set_size(size);
    allocation->set_count(count);
    allocation->set_stack_id(frame_id);
  }
  // TODO: Query PC Offsets to allow for offline symbolization.
}
