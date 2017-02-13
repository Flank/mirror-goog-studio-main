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

#include <stdio.h>
#include <unistd.h>

#include "slicer/slicer.h"

///////////////////////////////////////////////////////////////////////////////
//
int Dexter::Run() {
  bool show_help = false;
  int opt = 0;
  while ((opt = ::getopt(argc_, argv_, "hvo:")) != -1) {
    switch (opt) {
      case 'v':
        verbose_ = true;
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

///////////////////////////////////////////////////////////////////////////////
//
int Dexter::ProcessDex() {
  // TODO
  slicer::Test::Hello();
  return 0;
}

///////////////////////////////////////////////////////////////////////////////
//
void Dexter::PrintHelp() {
  printf("\nDex manipulation tool %s\n\n", VERSION);
  printf("dexter [flags...] [-o outfile] <dexfile>\n");
  printf(" -h : help\n");
  printf(" -v : verbose output\n");
  printf(" -o : output a new .dex file\n");
  printf("\n");
}
