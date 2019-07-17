#include "tools/base/deploy/common/env.h"

namespace deploy {

bool Env::IsValid() { return false; }

int Env::port() { return 0; }

std::string Env::root() { return ""; }

std::string Env::logcat() { return ""; }

std::string Env::shell() { return ""; }

int Env::uid() { return 0; }

void Env::set_uid(int uid) {}

}  // namespace deploy
