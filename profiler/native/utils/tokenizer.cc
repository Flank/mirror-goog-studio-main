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
#include "utils/tokenizer.h"

using std::vector;
using std::string;

namespace profiler {

vector<string> Tokenizer::GetTokens(const string &input,
                                    const string &delimiters) {
  vector<string> tokens{};
  string token;
  Tokenizer tokenizer(input, delimiters);
  while (tokenizer.GetNextToken(&token)) {
    tokens.push_back(token);
  }
  return tokens;
}

bool Tokenizer::GetNextToken(std::string *token) {
  EatDelimiters();
  return GetNextToken(
      token, [this](char c) { return delimiters_.find(c) == string::npos; });
}

bool Tokenizer::EatTokens(int32_t token_count) {
  int32_t remaining_count = token_count;
  while (remaining_count > 0) {
    if (!EatNextToken()) {
      return false;
    }
    remaining_count--;
  }
  return true;
}

bool Tokenizer::GetNextToken(std::string *token,
                             std::function<bool(char)> is_valid_char) {
  size_t start = index_;
  while (index_ < input_.size() && is_valid_char(input_[index_])) {
    index_++;
  }
  size_t end = index_;

  if (end > start) {
    if (token != nullptr) {
      *token = input_.substr(start, end - start);
    }
    return true;
  } else {
    return false;
  }
}

// Get the next character in the input text, unless we're already at the end
// of the text.
bool Tokenizer::GetNextChar(char *c) {
  if (done()) {
    return false;
  }

  if (c != nullptr) {
    *c = input_[index_];
  }
  set_index(index_ + 1);

  return true;
}

bool Tokenizer::EatDelimiters() {
  return EatWhile(
      [this](char c) { return delimiters_.find(c) != string::npos; });
}

bool Tokenizer::EatWhile(std::function<bool(char)> should_eat) {
  while (index_ < input_.size() && should_eat(input_[index_])) {
    index_++;
  }
  return true;
}

}  // namespace profiler
