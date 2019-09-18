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

package com.android.tools.sql;

import static com.android.tools.sql.protocol.SqliteInspection.*;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.inspection.Connection;
import androidx.inspection.Inspector;
import androidx.inspection.InspectorEnvironment;
import com.android.tools.idea.protobuf.*;
import com.android.tools.sql.protocol.SqliteInspection;
import com.android.tools.sql.protocol.SqliteInspection.TrackDatabasesResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SqlInspector extends Inspector {

    private AtomicInteger nextId = new AtomicInteger(1);
    private ConcurrentHashMap<Integer, DatabaseHandler> mDatabases =
            new ConcurrentHashMap<Integer, DatabaseHandler>();
    private ConcurrentHashMap<SQLiteDatabase, TableInvalidationTracker> mInvalidationTrackers =
            new ConcurrentHashMap<SQLiteDatabase, TableInvalidationTracker>();
    private final InspectorEnvironment environment;

    public SqlInspector(final Connection connection, InspectorEnvironment environment) {
        super(connection);
        this.environment = environment;
    }

    private void trackDatabases(ProtoCallback<TrackDatabasesResponse> callback) {
        callback.reply(TrackDatabasesResponse.getDefaultInstance());
        environment.registerExitHook(
                SQLiteDatabase.class,
                "openDatabase(Ljava/io/File;Landroid/database/sqlite/SQLiteDatabase$OpenParams;)Landroid/database/sqlite/SQLiteDatabase;",
                new InspectorEnvironment.ExitHook<SQLiteDatabase>() {
                    @Override
                    public SQLiteDatabase onExit(SQLiteDatabase database) {
                        log("database added");
                        onDatabasesAdded(database);
                        return database;
                    }
                });

        environment.registerEntryHook(
                SQLiteDatabase.class,
                "endTransaction()V",
                new InspectorEnvironment.EntryHook() {
                    @Override
                    public void onEntry(Object thisObject, List<Object> args) {
                        log("endTransaction happened");
                        TableInvalidationTracker invalidationTracker =
                                mInvalidationTrackers.get(thisObject);
                        if (invalidationTracker != null && !invalidationTracker.inTransaction) {
                            invalidationTracker.refreshVersionsAsync();
                        }
                    }
                });

        List<SQLiteDatabase> instances = environment.findInstances(SQLiteDatabase.class);
        for (SQLiteDatabase db : instances) {
            onDatabasesAdded(db);
        }
    }

    private void getSchema(int id, ProtoCallback<SchemaResponse> callback) {
        log("SqlInspector.getSchema");
        DatabaseHandler database = mDatabases.get(id);
        // check if null
        boolean open = database.isOpen();
        if (!open) {
            callback.reply(SchemaResponse.getDefaultInstance());
            return;
        }

        Cursor cursor = database.query("SELECT name FROM sqlite_master WHERE type='table'");
        ArrayList<String> tableNames = new ArrayList<String>();
        while (cursor.moveToNext()) {
            String table = cursor.getString(0);
            tableNames.add(table);
        }

        cursor.close();
        Schema.Builder schemaBuilder = Schema.newBuilder();
        for (String table : tableNames) {
            Table.Builder tableBuilder = Table.newBuilder();
            tableBuilder.setName(table);
            Cursor tableInfo = database.query("PRAGMA table_info(" + table + ")");
            int nameIndex = tableInfo.getColumnIndex("name");
            int typeIndex = tableInfo.getColumnIndex("type");
            while (tableInfo.moveToNext()) {
                Column column =
                        Column.newBuilder()
                                .setName(tableInfo.getString(nameIndex))
                                .setType(tableInfo.getString(typeIndex))
                                .build();
                tableBuilder.addColumns(column);
            }
            schemaBuilder.addTables(tableBuilder.build());
            tableInfo.close();
        }
        callback.reply(SchemaResponse.newBuilder().setSchema(schemaBuilder.build()).build());
    }

    private void onDatabasesAdded(SQLiteDatabase database) {
        onDatabasesAdded(new DatabaseHandler.AndroidDatabaseHandler(database));
    }

    private void onDatabasesAdded(DatabaseHandler handler) {
        // handle duplicates?
        int id = nextId.getAndIncrement();
        mDatabases.put(id, handler);
        String path = handler.getPath();
        if (path == null) {
            path = "in_memory_db";
        }
        sendEvent(
                SqliteInspection.Events.newBuilder()
                        .setDatabaseOpen(
                                DatabaseOpenedEvent.newBuilder().setId(id).setName(path).build())
                        .build());
    }

    private void sendEvent(AbstractMessageLite messageLite) {
        getConnection().sendEvent(messageLite.toByteArray());
    }

    @Override
    public void onReceiveCommand(byte[] bytes, CommandCallback commandCallback) {
        Commands commands;
        try {
            commands = Commands.parseFrom(bytes);
            if (commands.hasTrackDatabases()) {

                trackDatabases(new ProtoCallback<TrackDatabasesResponse>(commandCallback));
            } else if (commands.hasGetSchema()) {
                getSchema(
                        commands.getGetSchema().getId(),
                        new ProtoCallback<SchemaResponse>(commandCallback));
            } else if (commands.hasQuery()) {
                query(
                        commands.getQuery().getDatabaseId(),
                        commands.getQuery().getQuery(),
                        commands.getQuery().getAffectedTablesList(),
                        new ProtoCallback<SqliteInspection.Cursor>(commandCallback));
            } else if (commands.hasExec()) {
                exec(
                        commands.getExec().getDatabaseId(),
                        commands.getExec().getQuery(),
                        new ProtoCallback<SqliteInspection.Cursor>(commandCallback));
            } else {
                log("unknown commands " + commands);
            }
        } catch (InvalidProtocolBufferException e) {
            log(e);
        }
    }

    private void query(
            int databaseId,
            String query,
            List<String> affectedTables,
            ProtoCallback<SqliteInspection.Cursor> callback) {
        DatabaseHandler sqLiteDatabase = mDatabases.get(databaseId);
        Cursor cursor = sqLiteDatabase.query(query);

        SQLiteDatabase db = sqLiteDatabase.getDb();
        if (mInvalidationTrackers.get(db) == null) {
            mInvalidationTrackers.put(db, TableInvalidationTracker.createInvalidationTracker(db));
        }

        for (String table : affectedTables) {
            trackChanges(databaseId, db, table);
        }
        callback.reply(convert(cursor));
    }

    private void exec(
            int databaseId, String query, ProtoCallback<SqliteInspection.Cursor> callback) {
        try {
            DatabaseHandler sqLiteDatabase = mDatabases.get(databaseId);
            log("Executing write query:" + query);
            sqLiteDatabase.execSql(query);
            MatrixCursor cursor = new MatrixCursor(new String[] {"affected_rows"});
            cursor.addRow(new Object[] {0});
            callback.reply(convert(cursor));
            RoomInvalidationTrackerHelper.invokeTrackers(findRoomInvalidationTrackers(environment));
        } catch (Throwable th) {
            th.printStackTrace();
            log("received error : " + th.getMessage());
            MatrixCursor cursor = new MatrixCursor(new String[] {"affected_rows"});
            cursor.addRow(new Object[] {0});
            callback.reply(convert(cursor));
        }
    }

    private static SqliteInspection.Cursor convert(Cursor cursor) {
        SqliteInspection.Cursor.Builder cursorBuilder = SqliteInspection.Cursor.newBuilder();
        int columnCount = cursor.getColumnCount();
        while (cursor.moveToNext()) {
            Row.Builder rowBuilder = Row.newBuilder();
            for (int i = 0; i < columnCount; i++) {
                SqliteInspection.CellValue value = readValue(cursor, i);
                rowBuilder.addValues(value);
            }
            cursorBuilder.addRows(rowBuilder.build());
        }
        cursor.close();
        return cursorBuilder.build();
    }

    private static SqliteInspection.CellValue readValue(Cursor cursor, int index) {
        SqliteInspection.CellValue.Builder builder = SqliteInspection.CellValue.newBuilder();

        builder.setColumnName(cursor.getColumnName(index));
        switch (cursor.getType(index)) {
            case Cursor.FIELD_TYPE_BLOB:
                return builder.setBlobValue(ByteString.copyFrom(cursor.getBlob(index))).build();
            case Cursor.FIELD_TYPE_STRING:
                return builder.setStringValue(cursor.getString(index)).build();
            case Cursor.FIELD_TYPE_INTEGER:
                return builder.setIntValue(cursor.getInt(index)).build();
            case Cursor.FIELD_TYPE_FLOAT:
                builder.setFloatValue(cursor.getFloat(index)).build();
        }
        return builder.build();
    }

    private Map<Integer, Set<String>> trackedTable = new HashMap<Integer, Set<String>>();

    private void trackChanges(final int dbId, SQLiteDatabase db, final String tableName) {
        if (trackedTable.get(dbId) == null) {
            trackedTable.put(dbId, new HashSet<String>());
        }
        Set<String> tables = trackedTable.get(dbId);
        if (tables.contains(tableName)) {
            return;
        }
        tables.add(tableName);
        TableInvalidationTracker invalidationTracker = mInvalidationTrackers.get(db);

        invalidationTracker.addObserver(
                new TableInvalidationTracker.Observer(tableName) {
                    @Override
                    public void onInvalidated(Set<String> tables) {
                        for (String tableName : tables) {
                            SqliteInspection.TableUpdatedEvent event =
                                    SqliteInspection.TableUpdatedEvent.newBuilder()
                                            .setId(dbId)
                                            .setTableName(tableName)
                                            .build();
                            sendEvent(
                                    SqliteInspection.Events.newBuilder()
                                            .setTableUpdate(event)
                                            .build());
                        }
                        log("table invalidated " + tables);
                    }
                });
    }

    private static List<Object> findRoomInvalidationTrackers(InspectorEnvironment environment) {
        Class invalidationTrackerClass = null;
        try {
            invalidationTrackerClass = Class.forName("androidx.room.InvalidationTracker");
            log(invalidationTrackerClass);
        } catch (Throwable th) {
            log("couldn't find invalidationt racker class");
            return Collections.emptyList();
        }
        List<Object> invalidationInstances = environment.findInstances(invalidationTrackerClass);
        return invalidationInstances;
    }

    private static void log(Object msg) {
        System.out.println("SQL_INSPECTOR :" + msg.toString());
    }
}

class ProtoCallback<T extends AbstractMessageLite> {
    private final Inspector.CommandCallback callback;

    ProtoCallback(Inspector.CommandCallback callback) {
        this.callback = callback;
    }

    void reply(T t) {
        callback.reply(t.toByteArray());
    }
}
