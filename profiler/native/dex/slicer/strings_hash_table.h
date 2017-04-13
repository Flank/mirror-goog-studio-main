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

#pragma once

#include <vector>
#include <cstdint>
#include <memory>

namespace ir {

struct String;

// A specialized strings indexing, used for quick
// strings lookup in the .dex IR (it maps "C" strings to non-null ir::String*)
//
// The indexing is implemented as a incrementally resizable hash table: we
// split the logical hash table into two internal fixed size tables, the
// "full table" and a "insertion table": When the insertion table overflows,
// we allocate a larger hashtable to replace it and "insertion table" becomes
// the "full table" (the old "full table" is rehashed into the new hash table)
//
// Similar to open addressing hash tables, all the buckets
// are a single, contiguous array. But this table is growing and
// the collisions are handled as separate chains (using indexes
// instead of pointers).
//
// The result is faster than std::unordered_map and uses ~25% of
// the memory used by std::unordered_map<const char*, String*>
//
class StringsLookup {
 private:
  // the index type inside the bucket array
  using Index = uint32_t;

  static constexpr Index kInitialHashBuckets = (1 << 7) - 1;
  static constexpr Index kAvgChainLength = 2;
  static constexpr Index kInvalidIndex = static_cast<Index>(-1);
  static constexpr double kResizeFactor = 1.6;

  struct __attribute__((packed)) Bucket {
    String* string = nullptr;
    Index next = kInvalidIndex;
  };

  class HashTable {
   public:
    explicit HashTable(Index size);
    bool Insert(String* string);
    String* Lookup(const char* cstr, uint32_t hash_value) const;
    Index HashBuckets() const { return hash_buckets_; }
    void InsertAll(const HashTable& src);

   private:
    std::vector<Bucket> buckets_;
    const Index hash_buckets_;
  };

 public:
  StringsLookup() {
    // we start with full_table_ == nullptr
    insertion_table_.reset(new HashTable(kInitialHashBuckets));
  }

  ~StringsLookup() = default;

  StringsLookup(const StringsLookup&) = delete;
  StringsLookup& operator=(const StringsLookup&) = delete;

  // Insert a new, non-nullptr String* into the hash table
  // (we only store unique strings so the new string must
  // not be in the table already)
  void Insert(String* string);

  // Lookup an existing string
  // (returns nullptr if the string is not found)
  String* Lookup(const char* cstr) const;

 private:
  std::unique_ptr<HashTable> full_table_;
  std::unique_ptr<HashTable> insertion_table_;
};

}  // namespace ir
