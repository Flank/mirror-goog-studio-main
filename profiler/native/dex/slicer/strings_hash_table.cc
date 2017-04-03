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

#include "strings_hash_table.h"
#include "dex_ir.h"
#include "dex_utf8.h"

#include <assert.h>

namespace ir {

//  DBJ2a string hash
static uint32_t HashValue(const char* cstr) {
  uint32_t hash = 5381;  // DBJ2 magic prime value
  while (*cstr) {
    hash = ((hash << 5) + hash) ^ *cstr++;
  }
  return hash;
}

StringsLookup::HashTable::HashTable(Index size) : hash_buckets_(size) {
  // allocate space for the hash buckets + avg chain length
  buckets_.reserve(hash_buckets_ * kAvgChainLength);
  buckets_.resize(hash_buckets_);
}

// Similar to the "cellar" version of coalesced hashing,
// the buckets array is divided into a fixed set of entries
// addressable by the hash value [0 .. hash_buckets_) and
// extra buckets for the collision chains [hash_buckets_, buckets_.size())
// Unlike coalesced hashing, our "cellar" is growing so we don't actually
// have to coalesce any chains.
//
// Returns true if the insertion succeeded, false if the table overflows
// (we never insert more than the pre-reserved capacity)
//
bool StringsLookup::HashTable::Insert(String* string) {
  CHECK(string != nullptr);
  // overflow?
  if (buckets_.size() + 1 > buckets_.capacity()) {
    return false;
  }
  const char* cstr = string->c_str();
  Index bucket_index = HashValue(cstr) % hash_buckets_;
  if (buckets_[bucket_index].string == nullptr) {
    buckets_[bucket_index].string = string;
  } else {
    Bucket new_bucket = {};
    new_bucket.string = string;
    new_bucket.next = buckets_[bucket_index].next;
    buckets_[bucket_index].next = buckets_.size();
    buckets_.push_back(new_bucket);
  }
  return true;
}

String* StringsLookup::HashTable::Lookup(const char* cstr, uint32_t hash_value) const {
  assert(hash_value == HashValue(cstr));
  Index bucket_index = hash_value % hash_buckets_;
  for (Index index = bucket_index; index != kInvalidIndex;
       index = buckets_[index].next) {
    auto string = buckets_[index].string;
    if (string == nullptr) {
      assert(index < hash_buckets_);
      break;
    } else if (dex::Utf8Cmp(string->c_str(), cstr) == 0) {
      return string;
    }
  }
  return nullptr;
}

void StringsLookup::HashTable::InsertAll(const HashTable& src) {
  for (const auto& bucket : src.buckets_) {
    if (bucket.string != nullptr) {
      CHECK(Insert(bucket.string));
    }
  }
}

// Try to insert into the "insertion table". If that overflows,
// we allocate a new, larger hash table, move "full table" strings to it
// and "insertion table" becomes the new "full table".
void StringsLookup::Insert(String* string) {
  assert(Lookup(string->c_str()) == nullptr);
  if (!insertion_table_->Insert(string)) {
    std::unique_ptr<HashTable> new_hash_table(
        new HashTable(insertion_table_->HashBuckets() * kResizeFactor));
    if (full_table_) {
      new_hash_table->InsertAll(*full_table_);
    }
    CHECK(new_hash_table->Insert(string));
    full_table_ = std::move(insertion_table_);
    insertion_table_ = std::move(new_hash_table);
  }
}

// First look into the "full table" and if the string is
// not found there look into the "insertion table" next
String* StringsLookup::Lookup(const char* cstr) const {
  auto hash_value = HashValue(cstr);
  if (full_table_) {
    auto string = full_table_->Lookup(cstr, hash_value);
    if (string != nullptr) {
      return string;
    }
  }
  return insertion_table_->Lookup(cstr, hash_value);
}

}  // namespace ir
