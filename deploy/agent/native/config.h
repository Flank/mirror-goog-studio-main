#ifndef CONFIG_H
#define CONFIG_H

#include <memory>

#include "proto/config.pb.h"
#include "utils/log.h"

using std::string;
using std::unique_ptr;
using swapper::proto::Config;

namespace swapper {

// TODO: This should read from a file passed as an option, not from a
// comma-delimited string.
unique_ptr<Config> ParseConfig(char* input) {
  string options(input);

  uint idx = 0;
  string parsed_options[4];

  size_t start = 0;
  while (idx < 4) {
    size_t end = options.find(',', start);

    if (end != string::npos) {
      parsed_options[idx++] = options.substr(start, end - start);
      start = end + 1;
    } else {
      parsed_options[idx++] = options.substr(start, end);
      break;
    }
  }

  for (; idx < 4; ++idx) {
    parsed_options[idx] = "";
  }

  auto config = unique_ptr<Config>(new Config);
  config->set_package_name(parsed_options[0]);
  config->set_dex_dir(parsed_options[1]);
  config->set_restart_activity(parsed_options[2] == "true");  // Really gross.
  config->set_instrumentation_jar(parsed_options[3]);

  return config;
}

}  // namespace swapper

#endif