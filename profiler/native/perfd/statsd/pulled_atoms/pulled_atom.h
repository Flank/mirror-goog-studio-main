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
#ifndef PERFD_STATSD_PULLED_ATOMS_PULLED_ATOM_H_
#define PERFD_STATSD_PULLED_ATOMS_PULLED_ATOM_H_

#include "proto/statsd/atoms.pb.h"
#include "proto/statsd/shell_config.pb.h"

namespace profiler {

// Base class for pulled statsd atoms, which provides system events at a certain
// interval, e.g. network speed.
class PulledAtom {
 public:
  PulledAtom() = default;
  virtual ~PulledAtom() = default;

  // Returns the field ID of the atom, as defined in atoms.proto.
  virtual int32_t AtomId() = 0;
  // Builds a single pulled subscription, filling the fields of the given proto.
  virtual void BuildConfig(
      android::os::statsd::PulledAtomSubscription* pulled) = 0;
  // Callback to handle when an atom is received for this subscription.
  // Dispatched on a separate thread.
  virtual void OnAtomRecieved(const android::os::statsd::Atom& atom) = 0;
};
}  // namespace profiler

#endif  // PERFD_STATSD_PULLED_ATOMS_PULLED_ATOM_H_
