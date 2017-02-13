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

#include "dexter.h"
#include "slicer/common.h"
#include "slicer/reader.h"
#include "slicer/chronometer.h"

#include <stdio.h>
#include <unistd.h>
#include <memory>

void Dexter::PrintHelp() {
  printf("\nDex manipulation tool %s\n\n", VERSION);
  printf("dexter [flags...] [-o outfile] <dexfile>\n");
  printf(" -h : help\n");
  printf(" -v : verbose output\n");
  printf(" -o : output a new .dex file\n");
  printf(" -m : print .dex layout map\n");
  printf("\n");
}

int Dexter::Run() {
  bool show_help = false;
  int opt = 0;
  while ((opt = ::getopt(argc_, argv_, "hvmo:")) != -1) {
    switch (opt) {
      case 'v':
        verbose_ = true;
        break;
      case 'm':
        print_map_ = true;
        break;
      case 'o':
        out_dex_filename_ = ::optarg;
        break;
      default:
        show_help = true;
        break;
    }
  }

  if (show_help || ::optind + 1 != argc_) {
    PrintHelp();
    return 1;
  }

  dex_filename_ = argv_[::optind];
  return ProcessDex();
}

// print the layout map of the .dex sections
static void PrintDexMap(const dex::Reader& reader) {
  printf("\nSections summary: name, offset, size [count]\n");

  const dex::MapList& dexMap = *reader.DexMapList();
  for (dex::u4 i = 0; i < dexMap.size; ++i) {
    const dex::MapItem& section = dexMap.list[i];
    const char* sectionName = "UNKNOWN";
    switch (section.type) {
      case dex::kHeaderItem:
        sectionName = "HeaderItem";
        break;
      case dex::kStringIdItem:
        sectionName = "StringIdItem";
        break;
      case dex::kTypeIdItem:
        sectionName = "TypeIdItem";
        break;
      case dex::kProtoIdItem:
        sectionName = "ProtoIdItem";
        break;
      case dex::kFieldIdItem:
        sectionName = "FieldIdItem";
        break;
      case dex::kMethodIdItem:
        sectionName = "MethodIdItem";
        break;
      case dex::kClassDefItem:
        sectionName = "ClassDefItem";
        break;
      case dex::kMapList:
        sectionName = "MapList";
        break;
      case dex::kTypeList:
        sectionName = "TypeList";
        break;
      case dex::kAnnotationSetRefList:
        sectionName = "AnnotationSetRefList";
        break;
      case dex::kAnnotationSetItem:
        sectionName = "AnnotationSetItem";
        break;
      case dex::kClassDataItem:
        sectionName = "ClassDataItem";
        break;
      case dex::kCodeItem:
        sectionName = "CodeItem";
        break;
      case dex::kStringDataItem:
        sectionName = "StringDataItem";
        break;
      case dex::kDebugInfoItem:
        sectionName = "DebugInfoItem";
        break;
      case dex::kAnnotationItem:
        sectionName = "AnnotationItem";
        break;
      case dex::kEncodedArrayItem:
        sectionName = "EncodedArrayItem";
        break;
      case dex::kAnnotationsDirectoryItem:
        sectionName = "AnnotationsDirectoryItem";
        break;
    }

    dex::u4 sectionByteSize = (i == dexMap.size - 1)
                                  ? reader.Header()->file_size - section.offset
                                  : dexMap.list[i + 1].offset - section.offset;

    printf("  %-25s : %8x, %8x  [%u]\n", sectionName, section.offset,
           sectionByteSize, section.size);
  }
}

int Dexter::ProcessDex() {
  if (verbose_) {
    printf("\nReading: %s\n", dex_filename_);
  }

  // open input file
  FILE* in_file = fopen(dex_filename_, "rb");
  if (in_file == nullptr) {
    printf("Can't open input .dex file (%s)\n", dex_filename_);
    return 1;
  }

  // calculate file size
  fseek(in_file, 0, SEEK_END);
  size_t in_size = ftell(in_file);

  // allocate the in-memory .dex image buffer
  std::unique_ptr<dex::u1[]> in_buff(new dex::u1[in_size]);

  // read input .dex file
  fseek(in_file, 0, SEEK_SET);
  CHECK(fread(in_buff.get(), 1, in_size, in_file) == in_size);

  double reader_time = 0;

  // parse the .dex image
  {
    slicer::Chronometer chrono(reader_time);

    dex::Reader reader(in_buff.get(), in_size);

    // print the .dex map?
    if (print_map_) {
      PrintDexMap(reader);
    }

    // build the full .dex IR
    reader.CreateFullIR();
  }

  if (verbose_) {
    printf("\nDone (reader: %.3f ms)\n", reader_time);
  }

  // done
  fclose(in_file);
  return 0;
}
