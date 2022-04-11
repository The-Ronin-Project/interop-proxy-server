package com.projectronin.interop.proxy.server.test.util

import javax.sql.DataSource

/**
 * Makes backup copies of the tables listed in [tables] by appending "_backup" to their name and copying their data.
 */
fun backupTables(dataSource: DataSource, tables: List<String>) {
    val connection = dataSource.connection
    val sqlStatement = connection.createStatement()

    tables.map { sqlStatement.addBatch("CREATE TABLE ${it}_backup AS SELECT * FROM $it") }
    sqlStatement.executeBatch()

    connection.close()
}

/**
 * Restores data to the tables listed in [tables] by deleting what's currently there and replacing it with the data
 * from their backup table created with backupTables().  All the deletes will be executed first in the order they are in
 * [tables].  Then all of the inserts will be done in reverse order.
 * Make sure that if you restore a table, you also first restore all the tables that have constraints on it, or the
 * delete will fail and you'll get a BatchUpdateException when the insert tries to add duplicate data.
 */
fun restoreTables(dataSource: DataSource, tables: List<String>) {
    val connection = dataSource.connection
    val sqlStatement = connection.createStatement()

    tables.map { sqlStatement.addBatch("DELETE from $it;") }
    sqlStatement.executeBatch() // blow up on the deletes if there is a table you missed as a dependency
    tables.reversed().map { sqlStatement.addBatch("INSERT INTO $it SELECT * FROM ${it}_backup;") }
    sqlStatement.executeBatch()

    connection.close()
}

/**
 * Removes the backup tables created by backupTables()
 */
fun removeBackupTables(dataSource: DataSource, tables: List<String>) {
    val connection = dataSource.connection
    val sqlStatement = connection.createStatement()

    tables.map { sqlStatement.addBatch("DROP TABLE ${it}_backup;") }
    sqlStatement.executeBatch()

    connection.close()
}
