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
package com.android.layoutinspector.model;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

public class ViewNodeTableModel implements TableModel {

    private final List<TableModelListener> mListeners = Lists.newArrayList();

    private final List<ViewProperty> mEntries = Lists.newArrayList();

    private final Map<String, List<ViewProperty>> mGroupedProperties = Maps.newHashMap();

    public void setNode(@NonNull ViewNode node) {
        // Go through the properties, filtering the favorites properties first
        mEntries.clear();
        mEntries.addAll(node.properties);
        mGroupedProperties.clear();
        mGroupedProperties.putAll(node.groupedProperties);
        notifyChange(new TableModelEvent(this));
    }

    @Override
    public int getRowCount() {
        return mEntries.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return columnIndex == 0 ? "Property" : "Value";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ViewProperty p = mEntries.get(rowIndex);
        return columnIndex == 0 ? p.name : p.getValue();
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        // Not supported
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
        mListeners.add(l);
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
        mListeners.remove(l);
    }

    private void notifyChange(TableModelEvent event) {
        for (TableModelListener l : mListeners) {
            l.tableChanged(event);
        }
    }

    public Map<String, List<ViewProperty>> getGroupedProperties() {
        return mGroupedProperties;
    }
}
