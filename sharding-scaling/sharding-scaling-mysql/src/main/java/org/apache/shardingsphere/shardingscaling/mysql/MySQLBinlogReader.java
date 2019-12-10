/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingscaling.mysql;

import org.apache.shardingsphere.shardingscaling.core.config.JdbcDataSourceConfiguration;
import org.apache.shardingsphere.shardingscaling.core.config.RdbmsConfiguration;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.AbstractSyncRunner;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.channel.Channel;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.position.LogPosition;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.reader.LogReader;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.position.NopLogPosition;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.record.Column;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.record.DataRecord;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.record.FinishedRecord;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.record.PlaceholderRecord;
import org.apache.shardingsphere.shardingscaling.core.execute.executor.record.Record;
import org.apache.shardingsphere.shardingscaling.core.metadata.JdbcUri;
import org.apache.shardingsphere.shardingscaling.mysql.binlog.MySQLConnector;
import org.apache.shardingsphere.shardingscaling.mysql.binlog.event.AbstractBinlogEvent;
import org.apache.shardingsphere.shardingscaling.mysql.binlog.event.AbstractRowsEvent;
import org.apache.shardingsphere.shardingscaling.mysql.binlog.event.DeleteRowsEvent;
import org.apache.shardingsphere.shardingscaling.mysql.binlog.event.PlaceholderEvent;
import org.apache.shardingsphere.shardingscaling.mysql.binlog.event.UpdateRowsEvent;
import org.apache.shardingsphere.shardingscaling.mysql.binlog.event.WriteRowsEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.io.Serializable;

/**
 * MySQL binlog reader.
 *
 * @author avalon566
 * @author yangyi
 */
@Slf4j
public final class MySQLBinlogReader extends AbstractSyncRunner implements LogReader {

    private final BinlogPosition binlogPosition;

    private final RdbmsConfiguration rdbmsConfiguration;

    @Setter
    private Channel channel;

    public MySQLBinlogReader(final RdbmsConfiguration rdbmsConfiguration, final LogPosition binlogPosition) {
        this.binlogPosition = (BinlogPosition) binlogPosition;
        if (!JdbcDataSourceConfiguration.class.equals(rdbmsConfiguration.getDataSourceConfiguration().getClass())) {
            throw new UnsupportedOperationException("MySQLBinlogReader only support JdbcDataSourceConfiguration");
        }
        this.rdbmsConfiguration = rdbmsConfiguration;
    }

    @Override
    public void run() {
        start();
        read(channel);
    }

    @Override
    public void read(final Channel channel) {
        JdbcDataSourceConfiguration jdbcDataSourceConfiguration = (JdbcDataSourceConfiguration) rdbmsConfiguration.getDataSourceConfiguration();
        final JdbcUri uri = new JdbcUri(jdbcDataSourceConfiguration.getJdbcUrl());
        MySQLConnector client = new MySQLConnector(123456, uri.getHostname(), uri.getPort(), jdbcDataSourceConfiguration.getUsername(), jdbcDataSourceConfiguration.getPassword());
        client.connect();
        client.subscribe(binlogPosition.getFilename(), binlogPosition.getPosition());
        while (isRunning()) {
            AbstractBinlogEvent event = client.poll();
            if (null == event) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            if (event instanceof WriteRowsEvent) {
                handleWriteRowsEvent(channel, uri, (WriteRowsEvent) event);
            } else if (event instanceof UpdateRowsEvent) {
                handleUpdateRowsEvent(channel, uri, (UpdateRowsEvent) event);
            } else if (event instanceof DeleteRowsEvent) {
                handleDeleteRowsEvent(channel, uri, (DeleteRowsEvent) event);
            } else if (event instanceof PlaceholderEvent) {
                handlePlaceholderEvent(channel, (PlaceholderEvent) event);
            }
        }
        pushRecord(channel, new FinishedRecord(new NopLogPosition()));
    }

    private void handleWriteRowsEvent(final Channel channel, final JdbcUri uri, final WriteRowsEvent event) {
        if (filter(uri.getDatabase(), event.getTableName())) {
            return;
        }
        for (Serializable[] each : event.getAfterRows()) {
            DataRecord record = createDataRecord(event, each.length);
            record.setType("insert");
            for (Serializable eachColumnValue : each) {
                record.addColumn(new Column(eachColumnValue, true));
            }
            pushRecord(channel, record);
        }
    }

    private void handleUpdateRowsEvent(final Channel channel, final JdbcUri uri, final UpdateRowsEvent event) {
        if (filter(uri.getDatabase(), event.getTableName())) {
            return;
        }
        for (int i = 0; i < event.getBeforeRows().size(); i++) {
            Serializable[] beforeValues = event.getBeforeRows().get(i);
            Serializable[] afterValues = event.getAfterRows().get(i);
            DataRecord record = createDataRecord(event, beforeValues.length);
            record.setType("update");
            for (int j = 0; j < beforeValues.length; j++) {
                Object oldValue = beforeValues[j];
                Object newValue = afterValues[j];
                record.addColumn(new Column(newValue, !newValue.equals(oldValue)));
            }
            pushRecord(channel, record);
        }
    }

    private void handleDeleteRowsEvent(final Channel channel, final JdbcUri uri, final DeleteRowsEvent event) {
        if (filter(uri.getDatabase(), event.getTableName())) {
            return;
        }
        for (Serializable[] each : event.getBeforeRows()) {
            DataRecord record = createDataRecord(event, each.length);
            record.setType("delete");
            for (Serializable eachColumnValue : each) {
                record.addColumn(new Column(eachColumnValue, true));
            }
            pushRecord(channel, record);
        }
    }
    
    private DataRecord createDataRecord(final AbstractRowsEvent rowsEvent, final int columnCount) {
        DataRecord result = new DataRecord(new BinlogPosition(rowsEvent.getFileName(), rowsEvent.getPosition(), rowsEvent.getServerId()), columnCount);
        result.setFullTableName(rowsEvent.getTableName());
        result.setCommitTime(rowsEvent.getTimestamp());
        return result;
    }

    private void handlePlaceholderEvent(final Channel channel, final PlaceholderEvent event) {
        PlaceholderRecord record = new PlaceholderRecord(new BinlogPosition(event.getFileName(), event.getPosition(), event.getServerId()));
        pushRecord(channel, record);
    }

    private void pushRecord(final Channel channel, final Record record) {
        try {
            channel.pushRecord(record);
        } catch (InterruptedException ignored) {
        }
    }

    private boolean filter(final String database, final String fullTableName) {
        return !fullTableName.startsWith(database + ".");
    }
}