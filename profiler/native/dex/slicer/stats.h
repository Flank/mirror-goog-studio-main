/*
 * Copyright (C) 2017 The Android Open Source Project
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

#pragma once

#include <stdlib.h>
#include <stdio.h>
#include <string>
#include <memory>

namespace ir {
struct DexFile;
}

namespace slicer {

void PrintDexIrStats(std::shared_ptr<ir::DexFile> dexIr, bool useCsv);

// Simple integer metrics
#define STATS_FIELDS                    \
  SFIELD(buff_count)                    \
  SFIELD(buff_reallocs)                 \
  SFIELD(buff_size)                     \
  SFIELD(buff_capacity)                 \
  SFIELD(buff_alignments)               \
  SFIELD(buff_align_padding)

// Key timing stats
#define PERF_FIELDS                     \
  PFIELD(reader_time)                   \
  PFIELD(writer_time)                   \
  PFIELD(norm_time)

///////////////////////////////////////////////////////////////////////////////
//
// Misc single-value metrics
//
struct Stats {

#undef SFIELD
#define SFIELD(f) size_t f;
  STATS_FIELDS

  std::string name;

  void Print(bool csv) { csv ? PrintCsv() : PrintVerbose(); }

  void PrintVerbose() {
    printf("\nDex file statistics:\n");
#undef SFIELD
#define SFIELD(f) printf("  %-30s : %zu\n", #f, f);
    STATS_FIELDS
  }

  void PrintCsv() {
    // header
    printf("name, ");
#undef SFIELD
#define SFIELD(f) printf("%s, ", #f);
    STATS_FIELDS
    printf("\n");

    // values
    printf("%s, ", name.c_str());
#undef SFIELD
#define SFIELD(f) printf("%zu, ", f);
    STATS_FIELDS
    printf("\n");
  }
};

///////////////////////////////////////////////////////////////////////////////
//
// Performance metrics
//
struct Perf {

#undef PFIELD
#define PFIELD(f) double f;
  PERF_FIELDS

  std::string name;

  void Print(bool csv) { csv ? PrintCsv() : PrintVerbose(); }

  void PrintVerbose() {
    printf("\nPerf statistics:\n");
#undef PFIELD
#define PFIELD(f) printf("  %-30s : %.3f ms\n", #f, f);
    PERF_FIELDS
  }

  void PrintCsv() {
    // header
    printf("name, ");
#undef PFIELD
#define PFIELD(f) printf("%s, ", #f);
    PERF_FIELDS
    printf("\n");

    // values
    printf("%s, ", name.c_str());
#undef PFIELD
#define PFIELD(f) printf("%.3f, ", f);
    PERF_FIELDS
    printf("\n");
  }
};

#undef STATS_FIELDS
#undef SFIELD

#undef PERF_FIELDS
#undef PFIELD

extern Stats stats;
extern Perf perf;

} // namespace slicer

