/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "uid_fetcher.h"

namespace profiler {

bool UidFetcher::GetUidStringFromPidFile(std::string file_path,
                                         std::string *uid_result) {
  std::string content;
  FileReader::Read(file_path, &content);

  // Find the uid value start position. It's supposed to be after the prefix,
  // also after empty spaces on the same line.
  static const char *const kUidPrefix = "Uid:";
  size_t start = content.find(kUidPrefix);
  if (start != std::string::npos) {
    // Find the uid end position, which should be empty space or new line,
    // and check the uid value contains 0-9 only.
    Tokenizer tokenizer(content, " \t");
    tokenizer.set_index(start + strlen(kUidPrefix));
    tokenizer.SkipDelimiters();
    std::string uid;
    char next_char;
    if (tokenizer.GetNextToken(Tokenizer::IsDigit, &uid) &&
        tokenizer.GetNextChar(&next_char) &&
        Tokenizer::IsWhitespace(next_char)) {
      uid_result->assign(uid);
      return true;
    }
  }
  return false;
}
}  // namespace profiler