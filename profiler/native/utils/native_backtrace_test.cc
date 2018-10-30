/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "native_backtrace.h"

#include <algorithm>
#include <atomic>
#include <random>
#include <vector>

#include <gtest/gtest.h>
#define DONT_OPTIMIZE                                        \
  __attribute__((noinline)) __attribute__((optimize("-O0"))) \
      __attribute__((optnone))

using profiler::GetBacktrace;
using std::uintptr_t;
using std::vector;

struct BacktraceTestContext;
typedef void (*TestFunction)(BacktraceTestContext *context, size_t n);

struct BacktraceTestContext {
  int depth;
  int backtrace_size;
  vector<TestFunction> functions;
  // Approximate end addresses of corresponding function
  // from 'functions' vector.
  vector<uintptr_t> function_end_address;
  vector<uintptr_t> backtrace;
};

// This atomic variable provides a "side effect" visible from outside
// of the current thread. This prevents compiler from optimizing
// everything away and merging all FrameFunc<N> into a single one.
std::atomic<int> global_side_effect;

static uintptr_t DONT_OPTIMIZE GetCurrentIP() {
  global_side_effect++;
  return reinterpret_cast<uintptr_t>(__builtin_return_address(0));
}

template <int N>
void DONT_OPTIMIZE FrameFunc(BacktraceTestContext *context, size_t n) {
  global_side_effect += N;
  size_t next_n = n + 1;
  size_t depth = static_cast<size_t>(context->depth);
  if (next_n >= context->functions.size() || next_n >= depth) {
    context->backtrace = GetBacktrace(context->backtrace_size);
  } else {
    context->functions[next_n](context, next_n);
  }
  context->function_end_address[n] = GetCurrentIP();
  global_side_effect += N;
}

// Generates a random sequence of nested calls consisting of
// FrameFunc<N> calling each other. Each FrameFunc instantiation is unique
// and has its own address and body.
static BacktraceTestContext GetRandomTestContext(int count, int seed = 0) {
  BacktraceTestContext result;
  result.depth = count;
  result.backtrace_size = count;
  vector<TestFunction> functions = {FrameFunc<1>, FrameFunc<2>, FrameFunc<3>,
                                    FrameFunc<4>, FrameFunc<5>, FrameFunc<6>,
                                    FrameFunc<7>, FrameFunc<8>, FrameFunc<9>};

  functions.resize(static_cast<size_t>(count), FrameFunc<10>);
  std::mt19937 random_generator(seed);
  std::shuffle(functions.begin(), functions.end(), random_generator);

  result.functions = functions;
  result.function_end_address.resize(functions.size());
  return result;
}

// Checks that test infrastructure was correctly set up.
TEST(NativeBacktrace, TestInfraIsSane) {
  const int kCallDepth = 100;
  auto context = GetRandomTestContext(kCallDepth);
  context.functions[0](&context, 0);
  ASSERT_EQ(context.functions.size(), kCallDepth);
  ASSERT_EQ(context.backtrace_size, kCallDepth);
  ASSERT_EQ(context.depth, kCallDepth);
  for (int i = 0; i < context.depth; i++) {
    auto fun_address = reinterpret_cast<uintptr_t>(context.functions[i]);
    auto fun_end_address = context.function_end_address[i];
    EXPECT_GT(fun_end_address, fun_address)
        << "func end > func beggining. Frame #" << i;
  }
}

// Main test, that makes sure that backtrace correctly represents
// recursive call hierarchy created by test.
TEST(NativeBacktrace, FullBacktraceInCorrectOrder) {
  const int kCallDepth = 20;
  const int kTestIterationCount = 50;

  // Repeat different stack configurations kTestIterationCount times
  for (int iteration = 0; iteration < kTestIterationCount; iteration++) {
    auto context = GetRandomTestContext(kCallDepth, iteration);
    context.functions[0](&context, 0);
    ASSERT_EQ(context.backtrace_size, context.backtrace.size());
    auto bt_address = context.backtrace.begin();
    auto fun_address = context.functions.rbegin();
    auto fun_end_address = context.function_end_address.rbegin();

    // Check that for each frame backtrace produses an address
    // between a function entry point and a function end.
    for (int frame = 0; frame < context.backtrace_size; frame++) {
      EXPECT_GT(*bt_address, reinterpret_cast<uintptr_t>(*fun_address))
          << "backtrace address > func beggining. Frame #" << frame
          << " seed: " << iteration;
      EXPECT_LE(*bt_address, *fun_end_address)
          << "backtrace address <= func end. Frame #" << frame
          << " seed: " << iteration
          << " function address: " << reinterpret_cast<uintptr_t>(*fun_address);
      bt_address++;
      fun_address++;
      fun_end_address++;
    }
  }
}

// Checks that backtrace correctly truncates its output.
TEST(NativeBacktrace, TruncatedBacktrace) {
  const int kCallDepth = 100;
  const int kBacktraceDepth = 100;
  auto context = GetRandomTestContext(kCallDepth);
  context.backtrace_size = kBacktraceDepth;
  context.functions[0](&context, 0);
  ASSERT_EQ(context.backtrace_size, context.backtrace.size());
  auto bt_address = context.backtrace.begin();
  auto fun_address = context.functions.rbegin();
  auto fun_end_address = context.function_end_address.rbegin();
  for (int i = 0; i < context.backtrace_size; i++) {
    EXPECT_GT(*bt_address, reinterpret_cast<uintptr_t>(*fun_address))
        << "backtrace address > func beggining. Frame #" << i;
    EXPECT_LE(*bt_address, *fun_end_address)
        << "backtrace address <= func end. Frame #" << i;
    bt_address++;
    fun_address++;
    fun_end_address++;
  }
}

// Checks that backtrace can handle 0.
TEST(NativeBacktrace, EmptyBacktrace) {
  auto context = GetRandomTestContext(30);
  context.backtrace_size = 0;
  context.functions[0](&context, 0);
  ASSERT_EQ(context.backtrace_size, context.backtrace.size());
}
