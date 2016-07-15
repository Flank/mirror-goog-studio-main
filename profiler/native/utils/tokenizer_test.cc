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
#include "tokenizer.h"

#include <gtest/gtest.h>

using profiler::Tokenizer;
using std::string;
using std::vector;

TEST(Tokenizer, GetTokens_DelimiterNotPresentInInput) {
  string input("Twinkle Twinkle Little Star!");
  vector<string> tokens = Tokenizer::GetTokens(input, ":");
  EXPECT_EQ(1u, tokens.size());
  EXPECT_EQ(input, tokens[0]);
}

TEST(Tokenizer, GetTokens_HandlesMultipleDelimiters) {
  string input("test1test2test3test2test3test3");
  vector<string> tokens = Tokenizer::GetTokens(input, "123");
  EXPECT_EQ(6u, tokens.size());
  for (auto &token : tokens) {
    EXPECT_EQ("test", token);
  }
}

TEST(Tokenizer, GetTokens_LeadingAndTrailingDelimitersAreRemoved) {
  string input(" test test ");
  vector<string> tokens = Tokenizer::GetTokens(input, " ");
  EXPECT_EQ(2u, tokens.size());
  for (auto &token : tokens) {
    EXPECT_EQ("test", token);
  }
}

TEST(Tokenizer, GetTokens_ConsecutiveDelimitersAreRemoved) {
  string input("test32122333test");
  vector<string> tokens = Tokenizer::GetTokens(input, "123");
  EXPECT_EQ(2u, tokens.size());
  EXPECT_EQ("test", tokens[0]);
  for (auto &token : tokens) {
    EXPECT_EQ("test", token);
  }
}

TEST(Tokenizer, GetTokens_StartsInTheMiddle) {
  string input("first second three four");
  vector<string> tokens = Tokenizer::GetTokens(input, " ", 1);
  EXPECT_EQ(3u, tokens.size());
  EXPECT_EQ("second", tokens[0]);
  EXPECT_EQ("three", tokens[1]);
  EXPECT_EQ("four", tokens[2]);
}

TEST(Tokenizer, GetTokens_StartsAtLastTokenExpectEmptyResult) {
  string input("first second");
  vector<string> tokens = Tokenizer::GetTokens(input, " ", 2);
  EXPECT_EQ(0u, tokens.size());
}

TEST(Tokenizer, GetTokens_MaxTwoTokensAreSpecified) {
  string input("first second three four five");
  vector<string> tokens = Tokenizer::GetTokens(input, " ", 1, 2);
  EXPECT_EQ(2u, tokens.size());
  EXPECT_EQ("second", tokens[0]);
  EXPECT_EQ("three", tokens[1]);
}

TEST(Tokenizer, GetNextToken_GetFirstTokenWorks) {
  string input("first second");
  Tokenizer t(input);
  string token;

  EXPECT_TRUE(t.GetNextToken(&token));
  EXPECT_EQ("first", token);
}

TEST(Tokenizer, GetNextToken_GetFirstTokenWithDelimitersWorks) {
  string input("first;second");
  Tokenizer t(input, ";");
  string token;

  EXPECT_TRUE(t.GetNextToken(&token));
  EXPECT_EQ("first", token);
}

TEST(Tokenizer, EatNextToken_SkipsToken) {
  string input("first second");
  Tokenizer t(input);
  string token;

  EXPECT_TRUE(t.EatNextToken());
  EXPECT_TRUE(t.GetNextToken(&token));
  EXPECT_EQ("second", token);
}

TEST(Tokenizer, EatNextToken_ReturnsFalseIfNoToken) {
  string input("token");
  Tokenizer t(input);

  EXPECT_TRUE(t.EatNextToken());
  EXPECT_FALSE(t.EatNextToken());
}

TEST(Tokenizer, EatTokensWorks) {
  string input("first second third fourth fifth");
  Tokenizer t(input);
  string token;

  EXPECT_TRUE(t.EatTokens(3));
  EXPECT_TRUE(t.GetNextToken(&token));
  EXPECT_EQ("fourth", token);
}

TEST(Tokenizer, EatTokens_ReturnsFalseIfNotEnoughTokens) {
  string input("first second third fourth fifth");
  Tokenizer t(input);
  string token;

  EXPECT_FALSE(t.EatTokens(10));
}

TEST(Tokenizer, GetNextToken_LambdaAllowsCustomTokenRetrieval) {
  string input("ABC123ABC");
  Tokenizer t(input);
  string token;
  EXPECT_TRUE(t.GetNextToken(Tokenizer::IsAlpha, &token));
  EXPECT_EQ(token, "ABC");
  EXPECT_TRUE(t.GetNextToken(Tokenizer::IsDigit, &token));
  EXPECT_EQ(token, "123");
}

TEST(Tokenizer, GetNextChar_GetsNextCharInTextIncludingDelimiters) {
  string input("A B");
  Tokenizer t(input);
  char c;

  EXPECT_TRUE(t.GetNextChar(&c));
  EXPECT_EQ('A', c);
  EXPECT_TRUE(t.GetNextChar(&c));
  EXPECT_EQ(' ', c);
}

TEST(Tokenizer, GetNextChar_GetsNextCharFailsIfNoMoreChars) {
  string input("AB");
  Tokenizer t(input);
  char c;

  EXPECT_TRUE(t.GetNextChar(&c));
  EXPECT_TRUE(t.GetNextChar(&c));
  EXPECT_EQ(c, 'B');
  EXPECT_FALSE(t.GetNextChar(&c));
  EXPECT_EQ(c, 'B');
}

TEST(Tokenizer, EatNextChar_SkipsChar) {
  string input("AB");
  Tokenizer t(input);

  EXPECT_EQ(t.index(), 0u);
  t.EatNextChar();
  EXPECT_EQ(t.index(), 1u);
  t.EatNextChar();
  EXPECT_EQ(t.index(), 2u);
}

TEST(Tokenizer, EatDelimiters_AlwaysReturnsTrue) {
  string input("   ABC");
  Tokenizer t(input);

  EXPECT_EQ(t.index(), 0u);
  EXPECT_TRUE(t.EatDelimiters());
  EXPECT_EQ(t.index(), 3u);

  EXPECT_TRUE(t.EatDelimiters());
  EXPECT_EQ(t.index(), 3u);
}

TEST(Tokenizer, EatWhile_LambdaAllowsSkippingNonDelimeterCharacters) {
  string input("ABC123ABC");
  Tokenizer t(input);

  EXPECT_EQ(t.index(), 0u);
  EXPECT_TRUE(t.EatWhile(Tokenizer::IsAlpha));
  EXPECT_EQ(t.index(), 3u);

  EXPECT_TRUE(t.EatWhile(Tokenizer::IsDigit));
  EXPECT_EQ(t.index(), 6u);
}

TEST(Tokenizer, EatWhile_AlwaysReturnsTrue) {
  string input("ABC123ABC");
  Tokenizer t(input);

  EXPECT_EQ(t.index(), 0u);
  EXPECT_TRUE(t.EatWhile(Tokenizer::IsAlpha));
  EXPECT_EQ(t.index(), 3u);

  EXPECT_TRUE(t.EatWhile(Tokenizer::IsAlpha));
  EXPECT_EQ(t.index(), 3u);
}

TEST(Tokenizer, SetIndex_UpdatesIndex) {
  string input("ABC123ABC");
  Tokenizer t(input);

  EXPECT_EQ(t.index(), 0u);
  t.set_index(5u);
  EXPECT_EQ(t.index(), 5u);
}

TEST(Tokenizer, SetIndex_CanMoveBackward) {
  string input("ABC ABC");
  Tokenizer t(input);
  t.EatTokens(1);
  t.EatDelimiters();

  EXPECT_EQ(t.index(), 4u);
  t.set_index(0u);
  EXPECT_EQ(t.index(), 0u);
}

TEST(Tokenizer, SetIndex_ClampedToInput) {
  string input("ABC");
  Tokenizer t(input);

  EXPECT_EQ(t.index(), 0u);
  t.set_index(1000u);
  EXPECT_EQ(t.index(), 3u);
}

TEST(Tokenizer, IsAlpha) {
  string input("ABCxyz");
  Tokenizer t(input);

  t.EatNextToken(Tokenizer::IsAlpha);
  EXPECT_TRUE(t.done());
}

TEST(Tokenizer, IsAlphaNum) {
  string input("ABC123xyz");
  Tokenizer t(input);

  t.EatNextToken(Tokenizer::IsAlphaNum);
  EXPECT_TRUE(t.done());
}

TEST(Tokenizer, IsDigit) {
  string input("54321");
  Tokenizer t(input);

  t.EatNextToken(Tokenizer::IsDigit);
  EXPECT_TRUE(t.done());
}

TEST(Tokenizer, IsLower) {
  string input("abcxyz");
  Tokenizer t(input);

  t.EatNextToken(Tokenizer::IsLower);
  EXPECT_TRUE(t.done());
}

TEST(Tokenizer, IsUpper) {
  string input("ABCXYZ");
  Tokenizer t(input);

  t.EatNextToken(Tokenizer::IsUpper);
  EXPECT_TRUE(t.done());
}

TEST(Tokenizer, IsWhitespace) {
  string input(" \t \n \r \f ");
  Tokenizer t(input);

  t.EatNextToken(Tokenizer::IsWhitespace);
  EXPECT_TRUE(t.done());
}

TEST(Tokenizer, IsOneOf) {
  string input("ABCxyz");
  Tokenizer t(input);

  t.EatNextToken(Tokenizer::IsOneOf("xyzABC"));
  EXPECT_TRUE(t.done());
}
