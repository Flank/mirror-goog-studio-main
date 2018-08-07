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
#include <sstream>
#include <string>

#include "swap.pb.h"

std::string ReadFile(const std::string& file_name) {
  std::ostringstream file_contents;
  std::ifstream file(file_name);
  if (!file.is_open()) {
    std::cout << "Warning: Could not find file '" << file_name
              << "'. Using an empty dex buffer instead." << std::endl;
  }
  file_contents << file.rdbuf();
  return file_contents.str();
}

int HandleArgv(int argc, char** argv) {
  if (argc < 3) {
    std::cout
        << "Usage: proto_tool <package_name>,<should_restart>,[<class_name>, "
           "<dex_file>...]"
        << std::endl;
    return 1;
  }

  // Must pass the list of swap arguments in class/dex pairings.
  if ((argc - 3) % 2 != 0) {
    std::cout << "Every class name must be paired with a dex file. Class '"
              << argv[argc - 1] << "' did not have a dex file passed."
              << std::endl;
    return 1;
  }

  proto::SwapRequest swap_request;
  swap_request.set_package_name(argv[1]);
  swap_request.set_restart_activity(std::stoi(argv[2]));

  for (int i = 3; i + 1 < argc; i += 2) {
    proto::ClassDef* class_def = swap_request.add_classes();
    class_def->set_name(argv[i]);
    class_def->set_dex(ReadFile(argv[i + 1]));
  }

  std::string output;
  swap_request.SerializeToString(&output);
  std::cout << output << std::endl;

  return 0;
}

int HandleStdin() {
  std::cout << "Tool to manually create a SwapRequest, for testing."
            << std::endl;
  proto::SwapRequest swap_request;

  std::cout << "Package name? ";
  std::getline(std::cin, *swap_request.mutable_package_name());

  std::cout << "Restart activity (1 for yes, 0 for no)? ";
  std::string should_restart;
  std::getline(std::cin, should_restart);
  swap_request.set_restart_activity(std::stoi(should_restart));

  while (true) {
    proto::ClassDef* class_def = swap_request.add_classes();
    std::cout << "Name of class to swap? ";
    std::getline(std::cin, *class_def->mutable_name());

    if (class_def->name() == "") {
      break;
    }

    std::cout << "Dex file? ";
    std::string file_name;
    std::getline(std::cin, file_name);
    class_def->set_dex(ReadFile(file_name));
  }

  std::string file_name;
  std::cout << "File name for this proto? ";
  std::getline(std::cin, file_name);

  std::ofstream out_file(file_name, std::ios::binary);
  std::string swap_request_string;
  swap_request.SerializeToString(&swap_request_string);
  out_file << swap_request_string;
  out_file.close();

  return 0;
}

// Not sure if this should live here, but it's real nice to be able to invoke
// something from the command line to build a SwapRequest.
int main(int argc, char** argv) {
  if (argc == 1) {
    return HandleStdin();
  } else {
    return HandleArgv(argc, argv);
  }
}
