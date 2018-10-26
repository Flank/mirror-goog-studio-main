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

import com.android.annotations.VisibleForTesting;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Implementation of the {@link ApkFileDatabase} based on SQLite. */
public class SqlApkFileDatabase implements ApkFileDatabase {
    public static final int MAX_DEXFILES_ENTRY = 200;

    // Purely a value-based check. No plans to make the cache database forward / backward compatible.
    //  IE: All tables will be dropped if version number on the file does not match this number.
    private static final String CURRENT_SCHEMA_VERSION_NUMBER = "0.2";
    private final String schemaVersion;
    private final int maxDexFilesEntries;
    private Connection connection;

    public SqlApkFileDatabase(File file) {
        this(file, CURRENT_SCHEMA_VERSION_NUMBER, MAX_DEXFILES_ENTRY);
    }

    public SqlApkFileDatabase(File file, String schemaVersionNumber, int maxDexFileEntries) {
        this.schemaVersion = schemaVersionNumber;
        this.maxDexFilesEntries = maxDexFileEntries;
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
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

    private void fillTables() throws SQLException {
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

    private void flushOldCache() {
        try {
            executeUpdate(
                    "DELETE FROM dexfiles WHERE id < (SELECT * FROM (SELECT id from dexfiles ORDER BY id DESC LIMIT "
                            + maxDexFilesEntries
                            + ") ORDER BY id LIMIT 1);");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeStatements(String... statements) throws SQLException {
        try (Statement s = connection.createStatement()) {
            if (statements.length == 1) {
                s.execute(statements[0]);
            } else {
                for (String statement : statements) {
                    s.addBatch(statement);
                }
                s.executeBatch();
            }
        }
    }

    private int executeUpdate(String query) throws SQLException {
        try (Statement s = connection.createStatement()) {
            return s.executeUpdate(query);
        }
    }

    @Override
    public List<DexClass> getClasses(ApkEntry dex) {
        try (Statement s = connection.createStatement();
                ResultSet result =
                        s.executeQuery(
                                "SELECT classes.name as name, classes.checksum as checksum"
                                        + "  FROM dexfiles"
                                        + "  INNER JOIN archives on archives.dexfileId = dexfiles.id"
                                        + "  INNER JOIN classes on classes.dexfileId = dexfiles.id"
                                        + "  WHERE dexfiles.name = \""
                                        + dex.name
                                        + "\" AND dexfiles.checksum = "
                                        + dex.checksum
                                        + " ORDER BY id DESC")) {
            List<DexClass> classes = new ArrayList<>();
            while (result.next()) {
                String name = result.getString("name");
                long checksum = result.getLong("checksum");
                classes.add(new DexClass(name, checksum, null, dex));
            }
            return classes;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addClasses(List<DexClass> allClasses) {
        try {
            Map<Apk, Multimap<ApkEntry, DexClass>> map = new HashMap<>();
            for (DexClass clazz : allClasses) {
                Multimap<ApkEntry, DexClass> multimap = map.get(clazz.dex.apk);
                if (multimap == null) {
                    multimap = HashMultimap.create();
                    map.put(clazz.dex.apk, multimap);
                }
                multimap.put(clazz.dex, clazz);
            }
            for (Map.Entry<Apk, Multimap<ApkEntry, DexClass>> entry : map.entrySet()) {
                Multimap<ApkEntry, DexClass> classes = entry.getValue();
                List<Integer> ids = new ArrayList<>();
                for (ApkEntry dex : classes.keySet()) {
                    int id = addDexFile(dex.checksum, dex.name);
                    ids.add(id);
                    // TODO: Verify if writing all the classses in one go would help
                    addClasses(id, classes.get(dex));
                }
                addDexFiles(entry.getKey().checksum, ids);
            }
            flushOldCache();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int addDexFile(long checksum, String name) throws SQLException {
        String insert =
                String.format(
                        "INSERT INTO dexfiles(name, checksum) VALUES (\"%s\", %d);",
                        name, checksum);
        try (Statement s = connection.createStatement()) {
            int updated = s.executeUpdate(insert);
            assert updated == 1;
            try (ResultSet set = s.executeQuery("SELECT LAST_INSERT_ROWID();")) {
                return set.getInt(1);
            }
        }
    }

    private void addClasses(int dexId, Collection<DexClass> classes) throws SQLException {
        if (classes.isEmpty()) {
            return;
        }
        String values =
                classes.stream()
                        .map(e -> String.format("(%d, \"%s\", %d)", dexId, e.name, e.checksum))
                        .collect(Collectors.joining(","));
        String insert =
                String.format("INSERT INTO classes (dexfileId, name, checksum) VALUES %s;", values);
        int updated = executeUpdate(insert);
        assert updated == classes.size();
    }

    private void addDexFiles(String archiveChecksum, List<Integer> files) throws SQLException {
        if (files.isEmpty()) {
            return;
        }

        String values =
                files.stream()
                        .map(e -> String.format("(%d, \"%s\")", e, archiveChecksum))
                        .collect(Collectors.joining(","));
        String insert =
                String.format("INSERT INTO archives (dexfileId, checksum) VALUES %s;", values);
        int updated = executeUpdate(insert);
        assert updated == files.size();
    }

    @Override
    @VisibleForTesting
    public List<DexClass> dump() {
        List<DexClass> classes = new ArrayList<>();
        for (Apk apk : getApks()) {
            for (ApkEntry file : getFiles(apk)) {
                classes.addAll(getClasses(file));
            }
        }
        classes.sort(Comparator.comparingInt(a -> (int) a.checksum));
        return classes;
    }

    /** Test only code */
    private List<ApkEntry> getFiles(Apk apk) {
        try (Statement s = connection.createStatement();
                ResultSet result =
                        s.executeQuery(
                                "SELECT dexfiles.name as name, dexfiles.checksum as checksum FROM dexfiles"
                                        + "  INNER JOIN archives on archives.dexfileId = dexfiles.id"
                                        + "  WHERE archives.checksum = \""
                                        + apk.checksum
                                        + "\"")) {
            List<ApkEntry> files = new ArrayList<>();
            while (result.next()) {
                files.add(new ApkEntry(result.getString("name"), result.getLong("checksum"), apk));
            }
            return files;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Test only code */
    private List<Apk> getApks() {
        try (Statement s = connection.createStatement();
                ResultSet result = s.executeQuery("SELECT DISTINCT checksum from archives")) {
            List<Apk> apks = new ArrayList<>();
            while (result.next()) {
                apks.add(new Apk("", result.getString("checksum"), null, ImmutableList.of()));
            }
            return apks;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
