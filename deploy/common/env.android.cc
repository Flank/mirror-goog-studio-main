#include "tools/base/deploy/common/env.h"

#include <stdlib.h>
#include <sys/system_properties.h>

namespace deploy {

bool Env::IsValid() { return false; }

int Env::port() { return 0; }

std::string Env::root() { return ""; }

std::string Env::logcat() { return ""; }

std::string Env::shell() { return ""; }

int Env::api_level() {
  char sdk_ver_str[PROP_VALUE_MAX] = "0";
  __system_property_get("ro.build.version.sdk", sdk_ver_str);
  return atoi(sdk_ver_str);
}

std::string Env::build_type() {
  char type[PROP_VALUE_MAX] = "";
  __system_property_get("ro.build.type", type);
  return type;
}

int Env::uid() { return 0; }

void Env::set_uid(int uid) {}

}  // namespace deploy
