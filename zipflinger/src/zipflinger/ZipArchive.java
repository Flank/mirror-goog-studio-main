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

package zipflinger;

import com.android.annotations.NonNull;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZipArchive implements Closeable {

    private final FreeStore freestore;
    private boolean closed;
    private final File file;
    private final CentralDirectory cd;
    private final ZipWriter writer;
    /**
     * The object used to manipulate a zip archive.
     *
     * @param file the file object
     * @throws IOException
     */
    public ZipArchive(@NonNull File file) throws IOException {
        this.file = file;
        if (Files.exists(file.toPath())) {
            ZipMap map = ZipMap.from(file, true);
            cd = map.getCentralDirectory();
            freestore = new FreeStore(map.getEntries());
        } else {
            HashMap<String, Entry> entries = new HashMap<>();
            cd = new CentralDirectory(ByteBuffer.allocate(0), entries);
            freestore = new FreeStore(entries);
        }
        writer = new ZipWriter(file);
        closed = false;
    }

    /**
     * Returns the list of zip entries found in the archive. Note that these are the entries found
     * in the central directory via bottom-up parsing, not all entries present in the payload as a
     * top-down parser may return.
     *
     * @param file the zip archive to list.
     * @return the list of entries in the archive, parsed bottom-up (via the Central Directory).
     * @throws IOException
     */
    @NonNull
    public static Map<String, Entry> listEntries(@NonNull File file) throws IOException {
        return ZipMap.from(file, false).getEntries();
    }

    /**
     * Add a source to the archive.
     *
     * @param source The source to add to this zip archive.
     * @throws IllegalStateException if the entry name already exists in the archive.
     * @throws IOException if writing to the zip archive fails.
     */
    public void add(@NonNull Source source) throws IOException {
        if (closed) {
            throw new IllegalStateException(
                    String.format("Cannot add source to closed archive %s", file));
        }
        writeSource(source);
    }

    /**
     * Add a set of selected entries from an other zip archive.
     *
     * @param sources A zip archive with selected entries to add to this zip archive.
     * @throws IllegalStateException if the entry name already exists in the archive.
     * @throws IOException if writing to the zip archive fails.
     */
    public void add(@NonNull ZipSource sources) throws IOException {
        if (closed) {
            throw new IllegalStateException(
                    String.format("Cannot add zip source to closed archive %s", file));
        }

        try {
            sources.open();
            for (Source source : sources.getSelectedEntries()) {
                writeSource(source);
            }
        } finally {
            sources.close();
        }
    }

    /**
     * Delete an entry from this archive. If the entry did not exist, this method does nothing. To
     * avoid creating "holes" in the archive, it is mendatory to delete all entries first and add
     * sources second.
     *
     * @param name The name of the entry to delete.
     * @throws IllegalStateException if entries have been added.
     */
    public void delete(@NonNull String name) {
        if (closed) {
            throw new IllegalStateException(
                    String.format("Cannot delete '%s' from closed archive %s", name, file));
        }
        if (cd.containsNewEntries()) {
            throw new IllegalStateException("Delete entries after adding is illegal.");
        }
        Entry entry = cd.delete(name);
        if (entry != null) {
            freestore.free(entry.getLocation());
        }
    }

    /**
     * Carry all write operations to the storage system to reflect the delete/add operations
     * requested via add/delete methods.
     *
     * @throws IOException
     */
    // TODO: Zip64 -> Add boolean allowZip64
    @Override
    public void close() throws IOException {
        try {
            if (closed) {
                throw new IllegalStateException(
                        String.format("Attempt to close closed archive '%s'", file));
            }
            closed = true;
            finishArchive();
        } finally {
            writer.close();
        }
    }

    // 1. Fill empty space with virtual entries to be nice to top-down parsers.
    // 2. Write the CD.
    // 3. Write the EOCD.
    private void finishArchive() throws IOException {
        checkNumEntries();

        // Fill all empty space with virtual entry (not the last one since it represent all of
        // the unused file space.
        List<Location> freeLocations = freestore.getFreeLocations();
        for (int i = 0; i < freeLocations.size() - 1; i++) {
            fillFreeLocation(freeLocations.get(i), writer);
        }

        // Write the Central Directory
        Location lastFreeLocation = freestore.getLastFreeLocation();
        long cdStart = lastFreeLocation.first;
        writer.position(cdStart);
        cd.write(writer);
        Location cdLocation = new Location(cdStart, writer.position() - cdStart);

        // Write EOCD
        long numEntries = cd.getNumEntries();
        EndOfCentralDirectory.write(writer, cdLocation, numEntries);
        writer.truncate(writer.position());
    }

    // TODO: Zip64 -> Remove this check
    private void checkNumEntries() {
        // Check that num entries can be represented on an uint16_t.
        long numEntries = cd.getNumEntries();
        if (numEntries > Ints.USHRT_MAX) {
            throw new IllegalStateException("Too many entries (" + numEntries + ")");
        }
    }

    // Fill archive holes with virtual entries. Use extra field to fill as much as possible.
    private static void fillFreeLocation(@NonNull Location location, @NonNull ZipWriter writer)
            throws IOException {
        long spaceToFill = location.size();

        if (spaceToFill < LocalFileHeader.VIRTUAL_HEADER_SIZE) {
            // There is not enough space to create a virtual entry here. The FreeStore
            // never creates such gaps so it was already in the zip. Leave it as it is.
            return;
        }

        while (spaceToFill > 0) {
            long entrySize =
                    Math.min(Ints.USHRT_MAX - LocalFileHeader.VIRTUAL_HEADER_SIZE, spaceToFill);
            ByteBuffer virtualEntry =
                    ByteBuffer.allocate((int) (entrySize)).order(ByteOrder.LITTLE_ENDIAN);
            LocalFileHeader.fillVirtualEntry(virtualEntry);
            writer.write(virtualEntry, location.first + location.size() - spaceToFill);
            spaceToFill -= virtualEntry.capacity();
        }
    }

    private void writeSource(@NonNull Source source) throws IOException {
        source.prepare();
        validateName(source);

        // Calculate the size we need (header + payload)
        long headerSize = LocalFileHeader.sizeFor(source);
        long bytesNeeded = headerSize + source.getCompressedSize();

        // Allocate file space
        Location loc;
        int paddingForAlignment;
        if (source.isAligned()) {
            loc = freestore.alloc(bytesNeeded, headerSize, source.getAlignment());
            paddingForAlignment = (int) (loc.size() - bytesNeeded);
        } else {
            loc = freestore.ualloc(bytesNeeded);
            paddingForAlignment = 0;
        }

        CentralDirectoryRecord cdRecord =
                new CentralDirectoryRecord(
                        source.getNameBytes(),
                        source.getCrc(),
                        source.getCompressedSize(),
                        source.getUncompressedSize(),
                        loc.first,
                        source.getCompressionFlag(),
                        paddingForAlignment);
        cd.add(source.getName(), cdRecord);

        // Write LFH
        writer.position(loc.first);
        LocalFileHeader.writeEntry(cdRecord, writer);

        // Write payload
        source.writeTo(writer);
    }

    private void validateName(@NonNull Source source) {
        byte[] nameBytes = source.getNameBytes();
        String name = source.getName();
        if (nameBytes.length > Ints.USHRT_MAX) {
            throw new IllegalStateException(
                    String.format("Name '%s' is more than %d bytes", name, Ints.USHRT_MAX));
        }

        if (cd.contains(name)) {
            throw new IllegalStateException(String.format("Entry name '%s' collided", name));
        }
    }
}
