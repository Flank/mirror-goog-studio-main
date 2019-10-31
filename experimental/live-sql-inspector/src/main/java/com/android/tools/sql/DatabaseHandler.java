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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public interface DatabaseHandler {
    Cursor query(String query);

    void execSql(String query);

    boolean isOpen();

    String getPath();

    SQLiteDatabase getDb();

    public class AndroidDatabaseHandler implements DatabaseHandler {
        private final SQLiteDatabase db;

        public AndroidDatabaseHandler(SQLiteDatabase db) {
            this.db = db;
        }

        @Override
        public Cursor query(String query) {
            db.acquireReference();
            return db.rawQuery(query, new String[] {});
        }

        @Override
        public void execSql(String query) {
            db.acquireReference();
            db.execSQL(query);
        }

        @Override
        public SQLiteDatabase getDb() {
            return db;
        }

        @Override
        public boolean isOpen() {
            return db.isOpen();
        }

        @Override
        public String getPath() {
            return db.getPath();
        }
    }
}
