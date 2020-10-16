#include "thread_parser.h"

#include <gtest/gtest.h>

#include <string>

using std::string;

void ValidateThreadStat(string content, int32_t expectedTid,
                        string expectedName, char expectedState) {
  char state;
  string name;
  EXPECT_TRUE(profiler::ParseThreadStat(expectedTid, content, &state, &name));
  EXPECT_EQ(state, expectedState);
  EXPECT_EQ(name, expectedName);
}

TEST(ThreadParserTest, ParseTestSingleParen) {
  ValidateThreadStat(
      "16457 (MainThread-UE4) S 759 759 0 0 -1 1077952832 55654 6 0 0 303 54 0 "
      "2 20 0 87 0 6535872 7676690432 86829 18446744073709551615 1 1 0 0 0 0 "
      "4612 1 1073775864 0 0 0 17 0 0 0 0 0 0 0 0 0 0 0 0 0 0",
      16457, "MainThread-UE4", 'S');
}

TEST(ThreadParserTest, ParseTestMultiParen) {
  ValidateThreadStat(
      "16576 (OnlineA-ance(1)) S 759 759 0 0 -1 1077952576 13 6 0 0 167 248 0 "
      "2 10 -10 87 0 6535950 7676690432 86832 18446744073709551615 1 1 0 0 0 0 "
      "4612 1 1073775864 0 0 0 -1 3 0 0 0 0 0 0 0 0 0 0 0 0 0",
      16576, "OnlineA-ance(1)", 'S');
}