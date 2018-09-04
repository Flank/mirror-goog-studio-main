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
package com.android.tools.deploy.swapper;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Implementation of the {@link DexArchiveDatabase} based on SQLite. */
public class SQLiteDexArchiveDatabase extends DexArchiveDatabase {
    public static final int MAX_DEXFILES_ENTRY = 200;

    // Purely a value-based check. No plans to make the cache database forward / backward compatible.
    //  IE: All tables will be dropped if version number on the file does not match this number.
    private static final String CURRENT_SCHEMA_VERSION_NUMBER = "0.1";
    private final String schemaVersion;
    private Connection connection;

    public SQLiteDexArchiveDatabase(File file) {
        this(file, CURRENT_SCHEMA_VERSION_NUMBER);
    }

    public SQLiteDexArchiveDatabase(File file, String schemaVersionNumber) {
        this.schemaVersion = schemaVersionNumber;
        try {
            // For older versions of the JDBC we need to force load the sqlite.JDBC driver to trigger static initializer's and register
            // the JDBC driver with the Java DriverManager.
            Class.forName("org.sqlite.JDBC");
            boolean newFile = !file.exists();
            connection =
                    DriverManager.getConnection(String.format("jdbc:sqlite:%s", file.getPath()));
            if (newFile) {
                fillTables();
            } else {
                dropOldTables();
            }
            executeStatements("PRAGMA foreign_keys=ON;");
        } catch (ClassNotFoundException | SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    @Override
    public void close() {
        super.close();
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    private void dropOldTables() throws SQLException {
        try (Statement s = connection.createStatement();
                ResultSet result =
                        s.executeQuery(
                                "SELECT value from metadata WHERE name = \"schema-version\";")) {

            if (result.next()) {
                String version = result.getString("value");
                if (version.equals(schemaVersion)) {
                    return;
                }
            }
        } catch (SQLException ignored) {
            // If there is anything wrong, we are dropping all the tables.
        }

        // Otherwise we are dropping everything and starting over.

        executeStatements(
                "PRAGMA writable_schema = 1;",
                "delete from sqlite_master where type in ('table', 'index', 'trigger');",
                "PRAGMA writable_schema = 0;",
                "VACUUM;");
        fillTables();
    }

    private final void fillTables() throws SQLException {
        executeStatements(
                "CREATE TABLE metadata (name VARCHAR(255) UNIQUE NOT NULL, value VARCHAR(255) NOT NULL, PRIMARY KEY (name));",
                "INSERT INTO metadata (name, value) values (\"schema-version\", \""
                        + schemaVersion
                        + "\");",
                "CREATE TABLE dexfiles (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255) NOT NULL, checksum LONG NOT NULL);",
                "CREATE INDEX dexfiles_checksum_index ON dexfiles(checksum);",
                "CREATE TABLE archives (dexfileId INTEGER, checksum VARCHAR(255), "
                        + "CONSTRAINT fk_archives_dexfileId FOREIGN KEY(dexfileId) REFERENCES dexfiles(id) ON DELETE CASCADE);",
                "CREATE INDEX archives_checksum_index ON archives(checksum);",
                "CREATE TABLE classes (dexfileId INTEGER, name TEXT, checksum LONG, "
                        + "CONSTRAINT fk_classes_dexfileId FOREIGN KEY(dexfileId) REFERENCES dexfiles(id) ON DELETE CASCADE);",
                "CREATE INDEX classes_dexfileId_name_index ON classes(dexfileId);");
    }

    private void flushOldCache() throws SQLException {
        executeUpdate(
                "DELETE FROM dexfiles WHERE id < (SELECT * FROM (SELECT id from dexfiles ORDER BY id DESC LIMIT "
                        + MAX_DEXFILES_ENTRY
                        + ") ORDER BY id LIMIT 1);");
    }

    private void executeStatements(String... statements) throws SQLException {
        Statement s = connection.createStatement();
        if (statements.length == 1) {
            s.execute(statements[0]);
        } else {
            for (String statement : statements) {
                s.addBatch(statement);
            }
            s.executeBatch();
        }
        s.close();
    }

    private int executeUpdate(String query) throws SQLException {
        Statement s = connection.createStatement();
        int updated = s.executeUpdate(query);
        s.close();
        return updated;
    }

    @Override
    public Map<String, Long> getClassesChecksum(int dexFileIndex) {
        try (Statement s = connection.createStatement();
                ResultSet result =
                        s.executeQuery(
                                "SELECT name, checksum FROM classes WHERE dexfileId = "
                                        + dexFileIndex
                                        + ";")) {

            Map<String, Long> returnValue = new LinkedHashMap<>();
            while (result.next()) {
                String name = result.getString("name");
                Long checksum = result.getLong("checksum");
                returnValue.put(name, checksum);
            }
            return returnValue;
        } catch (SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    @Override
    public List<DexFileEntry> getDexFiles(String archiveChecksum) {
        try (Statement s = connection.createStatement();
                ResultSet result =
                        s.executeQuery(
                                "SELECT archives.dexfileId as id, dexfiles.name as name, dexfiles.checksum as checksum FROM "
                                        + " archives INNER JOIN dexfiles on archives.dexfileId = dexfiles.id where archives.checksum = \""
                                        + archiveChecksum
                                        + "\" ")) {
            List<DexFileEntry> returnValue = new LinkedList<>();
            while (result.next()) {
                int id = result.getInt("id");
                String name = result.getString("name");
                long checksum = result.getLong("checksum");
                returnValue.add(new DexFileEntry(id, checksum, name));
            }
            return returnValue;
        } catch (SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    @Override
    public int getDexFileIndex(long dexFileChecksum) {
        try (Statement s = connection.createStatement();
                ResultSet result =
                        s.executeQuery(
                                "SELECT id from dexfiles WHERE checksum ="
                                        + dexFileChecksum
                                        + ";")) {
            int index = -1;
            if (result.next()) {
                index = result.getInt("id");
            }
            return index;
        } catch (SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    @Override
    public int addDexFile(long dexFileChecksum, String name) {
        try {
            int updated =
                    executeUpdate(
                            "INSERT INTO dexfiles(name, checksum) VALUES (\""
                                    + name
                                    + "\", "
                                    + dexFileChecksum
                                    + ");");
            assert updated == 1;
            int index = getDexFileIndex(dexFileChecksum);
            flushOldCache();
            return index;
        } catch (SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    @Override
    public void fillEntriesChecksum(int dexFileIndex, Map<String, Long> classesChecksums) {
        String values =
                classesChecksums
                        .entrySet()
                        .stream()
                        .map(
                                e ->
                                        String.format(
                                                "(%d, \"%s\", %d)",
                                                dexFileIndex, e.getKey(), e.getValue()))
                        .collect(Collectors.joining(","));
        try {
            int updated =
                    executeUpdate(
                            "INSERT INTO classes (dexfileId, name, checksum) VALUES "
                                    + values
                                    + ";");
            assert updated == classesChecksums.size();
            flushOldCache();
        } catch (SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    @Override
    public void fillDexFileList(String archiveChecksum, List<Integer> dexFilesIndex) {
        String values =
                dexFilesIndex
                        .stream()
                        .map(e -> String.format("(%d, \"%s\")", e, archiveChecksum))
                        .collect(Collectors.joining(","));
        try {
            int updated =
                    executeUpdate(
                            "INSERT INTO archives (dexfileId, checksum) VALUES " + values + ";");
            assert updated == dexFilesIndex.size();
            flushOldCache();
        } catch (SQLException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }
}
