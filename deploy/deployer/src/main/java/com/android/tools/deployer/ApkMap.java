/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ApkMap {

    public static class Area {
        public Area(long start, long end) {
            this.start = start;
            this.end = end;
        }

        // TODO: Make start inclusive and end exclusive to be consistent with
        //       the rest of Java. Alternatively, move to (offset, length) pair
        //       representation.
        // Both offset are inclusive. The area start=1, end=2 has a size of two
        // where both offset 1 and offset 2 are included in the area.
        final long start;
        final long end;

        long size() {
            return end - start + 1;
        }
    }

    // ApkMap helps to build a map of the dirty Areas (dirty = has changed)
    // of a binary file, using only the known clean Areas.
    // The initial state is to mark everything as dirty:     [DDDDDDDDDDDDDDDDDD]
    // Calls to markClean() fraction the map into areas:     [    CCC           ]
    //                                                       [                C ]
    //                                                       [C                 ]
    // At the end of the process, getDirtyAreas returns.      [                  ]
    // a set of areas describing what is not clean     :     [ DDD   DDDDDDDDD D]
    private List<Area> cleanAreas = new ArrayList<>();
    private final long size;

    public ApkMap(long size) {
        this.size = size;
    }

    // It is expected that this new cleanArea does NOT overlap with any of the previously
    // declared clean areas. If not, the final dirty map will feature false negatives
    // (clear area will still be marked as dirty).
    public void markClean(Area cleanArea) {
        cleanAreas.add(cleanArea);
    }

    public List<Area> getDirtyAreas() {
        // Add markers at the beginning and the end so this algorithm is more straightforward.
        cleanAreas.add(new Area(-1, -1));
        cleanAreas.add(new Area(size, size));

        // Sort clear areas so they are in ascending order
        cleanAreas.sort((Area o1, Area o2) -> Long.compare(o1.start, o2.start));

        // Build a dirty map from a sorted list of clean non-overlapping areas.
        LinkedList<Area> dirtyAreas = new LinkedList<>();
        Iterator<Area> iterator = cleanAreas.iterator();
        Area previous = iterator.next();
        while (iterator.hasNext()) {
            Area current = iterator.next();
            Area dirtyArea = new Area(previous.end + 1, current.start - 1);
            if (dirtyArea.size() > 0) {
                dirtyAreas.add(dirtyArea);
            }
            previous = current;
        }

        return dirtyAreas;
    }
}
