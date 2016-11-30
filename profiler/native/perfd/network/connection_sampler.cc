/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include "connection_sampler.h"

#include "utils/file_reader.h"
#include "utils/tokenizer.h"

namespace {

using profiler::Tokenizer;
using std::string;

// Returns whether the next token is a valid heading which is the same as
// regex "[0-9]+:". For example, a valid heading is "01:"
//
// If successful, the heading token is consumed; otherwise, the tokenizer will
// be left wherever it failed and you shouldn't continue to use it.
bool EatValidHeading(Tokenizer *t) {
  char separator;
  return (t->EatNextToken(Tokenizer::IsDigit) && t->GetNextChar(&separator) &&
          separator == ':');
}

// Returns whether the next token is a valid IP address, which is the same as
// regex "[0-9]+:[0-9A-Za-z]{4}". If valid, the parameter |address| will be set
// to its value.
//
// If successful, the address is consumed; otherwise, the tokenizer will
// be left wherever it failed and you shouldn't continue to use it.
bool GetAddress(Tokenizer *t, string *address) {
  char separator;
  std::string port;
  if (t->GetNextToken(Tokenizer::IsAlphaNum, address) &&
      t->GetNextChar(&separator) && separator == ':' &&
      t->GetNextToken(Tokenizer::IsAlphaNum, &port) && port.size() == 4) {
    return true;
  }
  return false;
}

// Returns whether the next token is the ip address "127.0.0.1", coverted to
// ipv4 or ipv6 byte string. In other words, this will match either
// "0100007F:[0-9A-Za-z]{4}" or
// 0000000000000000FFFF00000100007F:[0-9A-Za-z]{4}".
//
// If successful, the IP address is consumed; otherwise, the tokenizer will
// be left wherever it failed and you shouldn't continue to use it.
bool EatLoopbackAddress(Tokenizer *t) {
  std::string address;
  if (GetAddress(t, &address)) {
    return address == "0100007F" ||
           address == "0000000000000000FFFF00000100007F";
  }
  return false;
}

// Returns whether the tokenizer is pointing at a line which represents a
// connection on a local interface (essentially, one loopback address talking
// to another).
//
// If successful, the tokenizer will be moved to right after the status code
// (the fourth token in the line); otherwise, the tokenizer will be left
// wherever it failed and you shouldn't continue to use it.
bool IsLocalInterface(Tokenizer *t) {
  // It's possible to have empty space in the beginning, for example, " 1:" has
  // empty space and "100:" does not have empty space.
  bool valid_heading = t->EatWhile(Tokenizer::IsWhitespace) &&
                       EatValidHeading(t) &&
                       t->EatWhile(Tokenizer::IsWhitespace);

  if (!valid_heading) {
    return false;
  }

  if (EatLoopbackAddress(t) && t->EatWhile(Tokenizer::IsWhitespace) &&
      EatLoopbackAddress(t) && t->EatWhile(Tokenizer::IsWhitespace)) {
    return true;
  }

  return false;
}
}  // namespace

namespace profiler {

using std::string;
using std::vector;

void ConnectionSampler::GetData(profiler::proto::NetworkProfilerData *data) {
  int connection_number = 0;
  for (auto &file_name : files_) {
    connection_number += ReadConnectionNumber(file_name);
  }
  data->mutable_connection_data()->set_connection_number(connection_number);
}

int ConnectionSampler::ReadConnectionNumber(const string &file) {
  vector<string> lines;
  FileReader::Read(file, &lines);

  int count = 0;
  for (const string &line : lines) {
    Tokenizer t(line);
    if (!IsLocalInterface(&t)) {
      t.set_index(0);
      string uid;
      if (t.EatTokens(kUidTokenIndex) && t.GetNextToken(&uid) && uid == uid_) {
        count++;
      }
    }
  }

  return count;
}

}  // namespace profiler
