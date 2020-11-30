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
package com.android.zipflinger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ZipArchive implements Archive {
    private final FreeStore freestore;
    private boolean closed;
    private final Path file;
    private final CentralDirectory cd;
    private final ZipWriter writer;
    private final ZipReader reader;
    private final Zip64.Policy policy;
    private ZipInfo zipInfo;
    private boolean modified;

    public ZipArchive(@NonNull Path file) throws IOException {
        this(file, Zip64.Policy.ALLOW);
    }

    /**
     * The object used to manipulate a zip archive.
     *
     * @param file the file object
     */
    public ZipArchive(@NonNull Path file, Zip64.Policy policy) throws IOException {
        this.file = file;
        this.policy = policy;
        if (Files.exists(file)) {
            ZipMap map = ZipMap.from(file, true, policy);
            zipInfo = new ZipInfo(map.getPayloadLocation(), map.getCdLoc(), map.getEocdLoc());
            cd = map.getCentralDirectory();
            freestore = new FreeStore(map.getEntries());
        } else {
            zipInfo = new ZipInfo();
            Map<String, Entry> entries = new LinkedHashMap<>();
            cd = new CentralDirectory(ByteBuffer.allocate(0), entries);
            freestore = new FreeStore(entries);
        }

        writer = new ZipWriter(file);
        reader = new ZipReader(file);
        closed = false;
        modified = false;
    }

    /** @deprecated Use ZipArchive(Path) instead. */
    @Deprecated
    public ZipArchive(@NonNull File file) throws IOException {
        this(file.toPath());
    }

    /** @deprecated Use {@link #ZipArchive(Path, Zip64.Policy)} instead. */
    @Deprecated
    public ZipArchive(@NonNull File file, Zip64.Policy policy) throws IOException {
        this(file.toPath(), policy);
    }

    /**
     * Returns the list of zip entries found in the archive. Note that these are the entries found
     * in the central directory via bottom-up parsing, not all entries present in the payload as a
     * top-down parser may return.
     *
     * @param file the zip archive to list.
     * @return the list of entries in the archive, parsed bottom-up (via the Central Directory).
     */
    @NonNull
    public static Map<String, Entry> listEntries(@NonNull Path file) throws IOException {
        return ZipMap.from(file, false).getEntries();
    }

    /** @deprecated Use {@link #listEntries(Path)} instead. */
    @Deprecated
    public static Map<String, Entry> listEntries(@NonNull File file) throws IOException {
        return listEntries(file.toPath());
    }

    @NonNull
    public List<String> listEntries() {
        return cd.listEntries();
    }

    @Nullable
    public ByteBuffer getContent(@NonNull String name) throws IOException {
        ExtractionInfo extractInfo = cd.getExtractionInfo(name);
        if (extractInfo == null) {
            return null;
        }
        Location loc = extractInfo.getLocation();
        ByteBuffer payloadByteBuffer = ByteBuffer.allocate(Math.toIntExact(loc.size()));
        reader.read(payloadByteBuffer, loc.first);
        if (extractInfo.isCompressed()) {
            return Compressor.inflate(payloadByteBuffer.array());
        } else {
            return payloadByteBuffer;
        }
    }

    /** See Archive.add documentation */
    @Override
    public void add(@NonNull Source source) throws IOException {
        if (closed) {
            throw new IllegalStateException(
                    String.format("Cannot add source to closed archive %s", file));
        }
        writeSource(source);
    }

    /** See Archive.add documentation */
    @Override
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

    /** See Archive.delete documentation */
    @Override
    public void delete(@NonNull String name) {
        if (closed) {
            throw new IllegalStateException(
                    String.format("Cannot delete '%s' from closed archive %s", name, file));
        }
        Location loc = cd.delete(name);
        if (loc.isValid()) {
            freestore.free(loc);
            modified = true;
        }
    }

    /**
     * Carry all write operations to the storage system to reflect the delete/add operations
     * requested via add/delete methods.
     */
    // TODO: Zip64 -> Add boolean allowZip64
    @Override
    public void close() throws IOException {
        closeWithInfo();
    }

    @NonNull
    public ZipInfo closeWithInfo() throws IOException {
        if (closed) {
            throw new IllegalStateException("Attempt to close a closed archive");
        }
        closed = true;
        try (ZipWriter w = writer;
                ZipReader r = reader) {
            writeArchive(w);
        }
        return zipInfo;
    }

    @NonNull
    public Path getPath() {
        return file;
    }

    public boolean isClosed() {
        return closed;
    }

    private void writeArchive(@NonNull ZipWriter writer) throws IOException {
        // There is no need to fill space and write footers if an already existing archive
        // has not been modified.
        if (zipInfo.eocd.isValid() && !modified) {
            return;
        }

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
        long numEntries = cd.getNumEntries();

        // Write zip64 EOCD and Locator (only if needed)
        writeZip64Footers(writer, cdLocation, numEntries);

        // Write EOCD
        Location eocdLocation = EndOfCentralDirectory.write(writer, cdLocation, numEntries);
        writer.truncate(writer.position());

        // Build and return location map
        Location payLoadLocation = new Location(0, cdStart);

        zipInfo = new ZipInfo(payLoadLocation, cdLocation, eocdLocation);
    }

    private void writeZip64Footers(
            @NonNull ZipWriter writer, @NonNull Location cdLocation, long numEntries)
            throws IOException {
        if (!Zip64.needZip64Footer(numEntries, cdLocation)) {
            return;
        }

        if (policy == Zip64.Policy.FORBID) {
            String message =
                    String.format(
                            "Zip64 required but forbidden (#entries=%d, cd=%s)",
                            numEntries, cdLocation);
            throw new IllegalStateException(message);
        }

        Zip64Eocd eocd = new Zip64Eocd(numEntries, cdLocation);
        Location eocdLocation = eocd.write(writer);

        Zip64Locator.write(writer, eocdLocation);
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
            long entrySize;
            if (spaceToFill <= LocalFileHeader.VIRTUAL_ENTRY_MAX_SIZE) {
                // Consume all the remaining space.
                entrySize = spaceToFill;
            } else {
                // Consume as much as possible while leaving enough for the next LFH entry.
                entrySize = Ints.USHRT_MAX;
            }
            int size = Math.toIntExact(entrySize);
            ByteBuffer virtualEntry = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
            LocalFileHeader.fillVirtualEntry(virtualEntry);
            writer.write(virtualEntry, location.first + location.size() - spaceToFill);
            spaceToFill -= virtualEntry.capacity();
        }
    }

    private void writeSource(@NonNull Source source) throws IOException {
        modified = true;
        validateName(source);
        source.prepare();

        // Calculate the size we need (header + payload)
        LocalFileHeader lfh = new LocalFileHeader(source);
        long headerSize = lfh.getSize();
        long bytesNeeded = headerSize + source.getCompressedSize();

        // Allocate file space
        Location loc;
        if (source.isAligned()) {
            loc = freestore.alloc(bytesNeeded, headerSize, source.getAlignment());
            lfh.setPadding(Math.toIntExact(loc.size() - bytesNeeded));
        } else {
            loc = freestore.ualloc(bytesNeeded);
        }

        writer.position(loc.first);
        lfh.write(writer);

        // Write payload
        long payloadStart = writer.position();
        long payloadSize = source.writeTo(writer);
        Location payloadLocation = new Location(payloadStart, payloadSize);

        // Update Central Directory record
        CentralDirectoryRecord cdRecord = new CentralDirectoryRecord(source, loc, payloadLocation);
        cd.add(source.getName(), cdRecord);

        checkPolicy(source, loc, payloadLocation);
    }

    private void checkPolicy(
            @NonNull Source source, @NonNull Location cdloc, @NonNull Location payloadLoc) {
        if (policy == Zip64.Policy.ALLOW) {
            return;
        }

        if (source.getUncompressedSize() >= Zip64.LONG_MAGIC
                || source.getCompressedSize() >= Zip64.LONG_MAGIC
                || cdloc.first >= Zip64.LONG_MAGIC
                || payloadLoc.first >= Zip64.LONG_MAGIC) {
            String message =
                    String.format(
                            "Zip64 forbidden but required in entry %s size=%d, csize=%d, cdloc=%s, loc=%s",
                            source.getName(),
                            source.getUncompressedSize(),
                            source.getCompressedSize(),
                            cdloc,
                            payloadLoc);
            throw new IllegalStateException(message);
        }
    }

    private void validateName(@NonNull Source source) {
        byte[] nameBytes = source.getNameBytes();
        String name = source.getName();
        if (nameBytes.length > Ints.USHRT_MAX) {
            throw new IllegalStateException(
                    String.format("Name '%s' is more than %d bytes", name, Ints.USHRT_MAX));
        }

        if (cd.contains(name)) {
            String template = "Zip file '%s' already contains entry '%s', cannot overwrite";
            String msg = String.format(template, file.toAbsolutePath().toString(), name);
            throw new IllegalStateException(msg);
        }
    }
}
