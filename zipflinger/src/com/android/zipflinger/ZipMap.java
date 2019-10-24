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
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class ZipMap {

    private static final int EOCD_MIN_SIZE = 22;
    private static final long EOCD_MAX_SIZE = Ints.USHRT_MAX + EOCD_MIN_SIZE;

    private final Map<String, Entry> entries = new HashMap<>();
    private CentralDirectory cd = null;

    // To build an accurate location of entries in the zip payload, data descriptors must be read.
    // This is not useful if an user only wants a list of entries in the zip but it is mandatory
    // if zip entries are deleted/added.
    private final boolean accountDataDescriptors;

    private File file;
    private long fileSize;

    private ZipMap(@NonNull File file, boolean accountDataDescriptors) {
        this.file = file;
        this.accountDataDescriptors = accountDataDescriptors;
    }

    @NonNull
    public static ZipMap from(@NonNull File zipFile, boolean accountDataDescriptors)
            throws IOException {
        ZipMap map = new ZipMap(zipFile, accountDataDescriptors);
        map.parse();
        return map;
    }

    private void parse() throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {

            fileSize = channel.size();

            // TODO: Zip64 -> Retrieve more to get the "zip64 end of central directory locator"
            int sizeToRead = Math.toIntExact(Math.min(fileSize, EOCD_MAX_SIZE));

            // Bring metadata to memory and parseRecord it.
            ByteBuffer buffer = ByteBuffer.allocate(sizeToRead).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer, fileSize - sizeToRead);
            Location cdLocation = getCDLocation(buffer);
            if (cdLocation == Location.INVALID) {
                throw new IllegalStateException(
                        String.format("Could not find CD in '%s'", file.toString()));
            }
            parseCentralDirectory(channel, cdLocation);
        }
    }

    private void parseCentralDirectory(@NonNull FileChannel channel, @NonNull Location location)
            throws IOException {
        int size = Math.toIntExact(location.size());
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        channel.read(buf, location.first);
        buf.rewind();

        while (buf.remaining() >= 4 && buf.getInt() == CentralDirectoryRecord.SIGNATURE) {
            Entry entry = new Entry();
            parseCentralDirectoryRecord(buf, channel, entry);
            if (!entry.getName().isEmpty()) {
                entries.put(entry.getName(), entry);
            }
        }

        cd = new CentralDirectory(buf, entries);

        sanityCheck(location);
    }

    private void sanityCheck(Location cdLocation) {
        //Sanity check that:
        //  - All payload locations are within the file (and not in the CD).
        for (Entry e : entries.values()) {
            Location loc = e.getLocation();
            if (loc.first < 0) {
                throw new IllegalStateException("Invalid first loc '" + e.getName() + "' " + loc);
            }
            if (loc.last >= fileSize) {
                throw new IllegalStateException(
                        fileSize + "Invalid last loc '" + e.getName() + "' " + loc);
            }
            Location cdLoc = e.getCdLocation();
            if (cdLoc.first < 0) {
                throw new IllegalStateException(
                        "Invalid first cdloc '" + e.getName() + "' " + cdLoc);
            }
            long cdSize = cdLocation.size();
            if (cdLoc.last >= cdSize) {
                throw new IllegalStateException(
                        cdSize + "Invalid last loc '" + e.getName() + "' " + cdLoc);
            }
        }
    }

    @NonNull
    private static Location getCDLocation(@NonNull ByteBuffer buffer) {
        // The buffer contains the end of the file. First try the most likely location.
        Location eocdLocation = null;

        buffer.position(buffer.position() - EOCD_MIN_SIZE);
        while (true) {
            int signature = buffer.getInt(); // Read 4 bytes.
            if (signature == EndOfCentralDirectory.SIGNATURE) {
                eocdLocation = new Location(buffer.position() - 4, EOCD_MIN_SIZE);
                break;
            }
            if (buffer.position() == 4) {
                break;
            }
            buffer.position(buffer.position() - Integer.BYTES - 1); //Backtrack 5 bytes.
        }

        // This is not a zip!
        if (eocdLocation == null) {
            throw new IllegalStateException("Unable to find EOCD signature");
        }

        return EndOfCentralDirectory.parse(buffer, eocdLocation);
    }

    @NonNull
    public Map<String, Entry> getEntries() {
        return entries;
    }

    @NonNull
    CentralDirectory getCentralDirectory() {
        return cd;
    }

    public void parseCentralDirectoryRecord(
            @NonNull ByteBuffer buf, @NonNull FileChannel channel, @NonNull Entry entry)
            throws IOException {
        long cdEntryStart = buf.position() - 4;

        buf.position(buf.position() + 4);
        //short versionMadeBy = buf.getShort();
        //short versionNeededToExtract = buf.getShort();
        short flags = buf.getShort();
        short compressionFlag = buf.getShort();
        entry.setCompressionFlag(compressionFlag);
        buf.position(buf.position() + 4);
        //short modTime = buf.getShort();
        //short modDate = buf.getShort();

        int crc = buf.getInt();
        entry.setCrc(crc);

        long compressedSize = Ints.uintToLong(buf.getInt());
        entry.setCompressedSize(compressedSize);

        long uncompressedSize = Ints.uintToLong(buf.getInt());
        entry.setUncompressedSize(uncompressedSize);

        int pathLength = Ints.ushortToInt(buf.getShort());
        int extraLength = Ints.ushortToInt(buf.getShort());
        int commentLength = Ints.ushortToInt(buf.getShort());
        buf.position(buf.position() + 8);
        //short diskNumber = buf.getShort();
        //short intAttributes = buf.getShort();
        //int extAttributes = bug.getInt();
        long start = buf.getInt(); // offset to local file entry header

        parseName(buf, pathLength, entry);

        // Retrieve the local header extra size since there are no guarantee it is the same as the
        // central directory size.
        // Semi-paranoid mode: Also check that the local name size is the same as the cd name size.
        ByteBuffer localFieldBuffer =
                readLocalFields(start + LocalFileHeader.OFFSET_TO_NAME, entry, channel);
        int localPathLength = Ints.ushortToInt(localFieldBuffer.getShort());
        int localExtraLength = Ints.ushortToInt(localFieldBuffer.getShort());
        if (pathLength != localPathLength) {
            String message =
                    String.format(
                            "Entry '%s' name differ (%d vs %d)",
                            entry.getName(), localPathLength, pathLength);
            throw new IllegalStateException(message);
        }

        // TODO Test the impact on performance to read the LFH. This is only useful to check if general status
        // flag differs from the CDR which so far does not seem to happen.

        // Skip extra field
        buf.position(buf.position() + extraLength);

        // Skip comment field
        buf.position(buf.position() + commentLength);

        // At this point we have everything we need to calculate payload location.
        boolean isCompressed = compressionFlag != 0;
        long payloadSize = isCompressed ? compressedSize : uncompressedSize;
        long end =
                start
                        + LocalFileHeader.LOCAL_FILE_HEADER_SIZE
                        + pathLength
                        + localExtraLength
                        + payloadSize;
        entry.setLocation(new Location(start, end - start));

        Location payloadLocation =
                new Location(
                        start
                                + LocalFileHeader.LOCAL_FILE_HEADER_SIZE
                                + pathLength
                                + localExtraLength,
                        payloadSize);
        entry.setPayloadLocation(payloadLocation);

        // At this point we have everything we need to calculate CD location.
        long cdEntrySize = CentralDirectoryRecord.SIZE + pathLength + extraLength + commentLength;
        entry.setCdLocation(new Location(cdEntryStart, cdEntrySize));

        // Parse data descriptor to adjust crc, compressed size, and uncompressed size.
        boolean hasDataDescriptor =
                ((flags & CentralDirectoryRecord.DATA_DESCRIPTOR_FLAG)
                        == CentralDirectoryRecord.DATA_DESCRIPTOR_FLAG);
        if (hasDataDescriptor) {
            if (accountDataDescriptors) {
                // This is expensive. Fortunately ZIP archive rarely use DD nowadays.
                channel.position(end);
                parseDataDescriptor(channel, entry);
            } else {
                entry.setLocation(Location.INVALID);
            }
        }
    }

    private ByteBuffer readLocalFields(long offset, Entry entry, FileChannel channel)
            throws IOException {
        // The extra field is not guaranteed to be the same in the LFH and in the CDH. In practice there is
        // often padding space that is not in the CD. We need to read the LFH.
        ByteBuffer localFieldsBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        if (offset < 0 || (offset + 4) > fileSize) {
            throw new IllegalStateException(
                    "Entry :" + entry.getName() + " invalid offset (" + offset + ")");
        }
        channel.read(localFieldsBuffer, offset);
        localFieldsBuffer.rewind();
        return localFieldsBuffer;
    }

    private static void parseName(@NonNull ByteBuffer buf, int length, @NonNull Entry entry) {
        byte[] pathBytes = new byte[length];
        buf.get(pathBytes);
        entry.setNameBytes(pathBytes);
    }

    private static void parseDataDescriptor(@NonNull FileChannel channel, @NonNull Entry entry)
            throws IOException {
        // If zip entries have data descriptor, we need to go an fetch every single entry to look if
        // the "optional" marker is there. Adjust zip entry area accordingly.

        ByteBuffer dataDescriptorBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        channel.read(dataDescriptorBuffer);
        dataDescriptorBuffer.rewind();

        int dataDescriptorLength = 12;
        if (dataDescriptorBuffer.getInt() == CentralDirectoryRecord.DATA_DESCRIPTOR_SIGNATURE) {
            dataDescriptorLength += 4;
        }

        Location adjustedLocation =
                new Location(
                        entry.getLocation().first,
                        entry.getLocation().size() + dataDescriptorLength);
        entry.setLocation(adjustedLocation);
    }

    public File getFile() {
        return file;
    }
}
