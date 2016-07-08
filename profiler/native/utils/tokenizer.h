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
#ifndef UTILS_TOKENIZER_H_
#define UTILS_TOKENIZER_H_

#include <cctype>
#include <cstring>
#include <functional>
#include <string>
#include <vector>

namespace profiler {

// Class which helps break apart a string into tokens separated by delimiters.
// Example:
//    Tokenizer t("1 1 3 5 8 13"); // Delimiter defaults to whitespace
//    string token;
//    t.EatTokens(4); // Skip over '1', '1', '3', and '5'
//    t.GetNextToken(&token); // token == "8"
//    t.GetNextToken(&token); // token == "13"
//    t.GetNextToken(&token); // Returns false, token still "13"
//
// Lambda support is provided for more refined parsing:
//    Tokenizer t("123+321=444");
//    string token;
//    t.GetNextToken(&token, Tokenizer::IsDigit); // 123
//    t.EatNextChar();                            // Skip over +
//    t.GetNextToken(&token, Tokenizer::IsDigit); // 321
//    ...
//
// Like the string class, this class handles bytes independently of the encoding
// used. If the input string being tokenized contains variable-length, multi-
// byte characters, this class will be unaware, operating on one byte at a time.
class Tokenizer final {
 public:
  static bool IsAlpha(char c) { return isalpha(c) != 0; }
  static bool IsAlphaNum(char c) { return isalnum(c) != 0; }
  static bool IsDigit(char c) { return isdigit(c) != 0; }
  static bool IsLower(char c) { return islower(c) != 0; }
  static bool IsUpper(char c) { return isupper(c) != 0; }
  static bool IsWhitespace(char c) { return strchr(kWhitespace, c) != nullptr; }
  // Creates a testing function which can be used with |GetNextToken|
  // e.g. GetNextToken(&token, Tokenizer::IsOneOf("abc"));
  static std::function<bool(char)> IsOneOf(const char *chars) {
    return [chars](char c) { return strchr(chars, c) != nullptr; };
  }

  // Returns the tokens by splitting |input| string by |delimiters|.
  static std::vector<std::string> GetTokens(const std::string &input,
                                            const std::string &delimiters);

  // Create a tokenizer that wraps an input string. By default, it will use
  // whitespace as a delimiter, but you can instead optionally specify a string
  // that contains one (or more) delimiters. If multiple delimiters are
  // specified, each one of them can separately indicate a token boundary.
  Tokenizer(const std::string &input,
            const std::string &delimiters = kWhitespace)
      : input_(input), delimiters_(delimiters), index_(0) {}

  // Get the next token in the input text, where a token is text surrounded by
  // delimiters. If found, return true and set |token| to its value. Otherwise,
  // |token| will be left unset.
  //
  // This method will eat any leading delimiters first. If this causes the index
  // to move to the end of the input string, then this method returns false and
  // leaves |token| unset.
  //
  // After this method is called, the index will be positioned after the end of
  // the token.
  //
  // If you don't care about the token result, use |EatNextToken| instead.
  bool GetNextToken(std::string *token);

  // Like |GetNextToken(token)|, but which uses a custom lambda method to
  // pull out the token (which, here, does not rely on delimiters, but is the
  // longest substring where the |is_valid_char| function returns true on each
  // character).
  bool GetNextToken(std::string *token,
                    std::function<bool(char)> is_valid_char);

  // Get the next character in the input text and return true, unless we're
  // already at the end of the text, at which point false will be returned and
  // |c| will be left unset.
  //
  // This method doesn't take delimiters into account and will return the next
  // character even if it is a delimiter.
  //
  // After this method is called, the index will be positioned one character
  // forward.
  //
  // If you don't care about the character result, use |EatNextChar| instead.
  bool GetNextChar(char *c);

  // Convenience method for calling |GetNextToken| when you don't care about the
  // token result.
  bool EatNextToken() { return GetNextToken(nullptr); }

  // Convenience method for calling |GetNextToken| when you don't care about the
  // token result.
  bool EatNextToken(std::function<bool(char)> is_valid_char) {
    return GetNextToken(nullptr, is_valid_char);
  }

  // Convenience method for calling |GetNextChar| when you don't care about the
  // character result.
  bool EatNextChar() { return GetNextChar(nullptr); }

  // Skip over |token_count| number of tokens, returning true if it could skip
  // over that many.
  //
  // After this method is called, the index will be positioned after the end of
  // the last token skipped.
  bool EatTokens(int32_t token_count);

  // If the tokenizer is currently pointing at any character which is a
  // delimiter, keep skipping over until all are passed. If not pointing at a
  // delimiter, this method leaves the tokenizer at its current index.
  //
  // This method always returns true, so that it can be chained safely without
  // breaking flow, e.g. GetToken && EatDelimeters && GetToken
  bool EatDelimiters();

  // If the tokenizer is currently pointing at any character matched by
  // |should_skip|, keep skipping over until all are passed. If |should_skip|
  // returns false immediately, this method leaves the tokenizer at its current
  // index.
  //
  // This method always returns true, so that it can be chained safely without
  // breaking flow, e.g. GetToken && EatWhile && GetToken
  bool EatWhile(std::function<bool(char)> should_eat);

  // Set the tokenizer's index directly, although it will be clamped to the
  // length of the input text.
  //
  // This method is useful if you want to reset the tokenizer or start
  // tokenizing from the middle of a string.
  void set_index(size_t index) {
    index_ = index;
    if (index_ > input_.size()) {
      index_ = input_.size();
    }
  }

  size_t index() const { return index_; }
  bool done() const { return index_ == input_.size(); }

 private:
  static constexpr const char *const kWhitespace = " \t\r\n\f";

  const std::string input_;
  const std::string delimiters_;
  size_t index_;
};

}  // namespace profiler

#endif  // UTILS_TOKENIZER_H_
