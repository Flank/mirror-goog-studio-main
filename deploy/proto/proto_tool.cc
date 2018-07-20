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

#include <fstream>
#include <iostream>
#include <string>

#include "swap.pb.h"

// Not sure if this should live here, but it's real nice to be able to invoke
// something from the command line to build a SwapRequest.
int main(int argc, char** argv) {
  std::cout << "Tool to manually create a SwapRequest, for testing."
            << std::endl;
  proto::SwapRequest swap_request;

  std::cout << "Package name? ";
  std::getline(std::cin, *swap_request.mutable_package_name());

  std::cout << "Restart activity (1 for yes, 0 for no)? ";
  std::string should_restart;
  std::getline(std::cin, should_restart);
  swap_request.set_restart_activity(std::stoi(should_restart));

  bool more_classes = true;
  while (more_classes) {
    proto::ClassDef* class_def = swap_request.add_classes();
    std::cout << "Name of class to swap? ";
    std::getline(std::cin, *class_def->mutable_name());

    if (class_def->name() == "") {
      break;
    }

    std::cout << "Dex file? ";
    std::getline(std::cin, *class_def->mutable_dex());
  }

  std::string file_name;
  std::cout << "File name for this proto? ";
  std::getline(std::cin, file_name);

  std::ofstream out_file(file_name, std::ios::binary);
  swap_request.SerializeToOstream(&out_file);
  out_file.close();

  return 0;
}