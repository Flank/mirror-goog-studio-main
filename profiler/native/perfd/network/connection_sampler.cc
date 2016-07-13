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

// Returns whether the next token is a valid heading which is the same as
// regex "[0-9]+:". For example, a valid heading is "01:"
//
// If successful, the heading token is consumed; otherwise, the tokenizer will
// be left wherever it failed and you shouldn't continue to use it.
bool IsValidHeading(Tokenizer *t) {
  char separator;
  return (t->EatNextToken(Tokenizer::IsDigit) && t->GetNextChar(&separator) &&
          separator == ':');
}

// Returns whether the next token is an ip address of all zeros, which is the
// same as regex "0+:[0-9A-Za-z]{4}". For example, an all zeros IP address is
// "00000000000000000:A12B"
//
// If successful, the IP address is consumed; otherwise, the tokenizer will
// be left wherever it failed and you shouldn't continue to use it.
bool IsAllZerosIpAddress(Tokenizer *t) {
  // TODO: This logic ignores the {4} part of the regex, do we need to be
  // stricter?
  char separator;
  return (t->EatNextToken(Tokenizer::IsOneOf("0")) &&
          t->GetNextChar(&separator) && separator == ':' &&
          t->EatNextToken(Tokenizer::IsAlphaNum));
}

// Returns whether the tokenizer is pointing at a line which represents a
// local interface, which we identify when both remote and local ip addresses
// are all zeroes and the connection status is listening ('0A').
//
// For example, this represents a local interface connection:
// " 01: 00000000000000000000000000000000:13B4
// 00000000000000000000000000000000:0000 0A ...".
//
// If successful, the tokenizer will be moved to right after the status code
// (the fourth token in the line); otherwise, the tokenizer will be left
// wherever it failed and you shouldn't continue to use it.
bool IsLocalInterface(Tokenizer *t) {
  // It's possible to have empty space in the beginning, for example, " 1:" has
  // empty space and "100:" does not have empty space.
  t->EatWhile(Tokenizer::IsWhitespace);
  bool match = IsValidHeading(t) && t->EatWhile(Tokenizer::IsWhitespace) &&
               IsAllZerosIpAddress(t) && t->EatWhile(Tokenizer::IsWhitespace) &&
               IsAllZerosIpAddress(t) && t->EatWhile(Tokenizer::IsWhitespace);

  if (match) {
    char c;
    if (t->GetNextChar(&c) && c == '0' && t->GetNextChar(&c) &&
        (c == 'A' || c == 'a')) {
      return true;
    }
  }
  return false;
}
}

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
