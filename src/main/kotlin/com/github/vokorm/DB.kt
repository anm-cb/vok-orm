package com.github.vokorm

import com.github.vokorm.VokOrm.dataSourceConfig
import com.github.vokorm.VokOrm.databaseAccessorProvider
import com.github.vokorm.VokOrm.destroy
import com.github.vokorm.VokOrm.init
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.sql2o.Connection
import java.io.Closeable
import javax.sql.DataSource
import javax.validation.NoProviderFoundException
import javax.validation.Validation
import javax.validation.Validator

/**
 * Initializes the ORM in the current JVM. By default uses the [HikariDataSourceAccessor] which uses [javax.sql.DataSource] pooled with HikariCP.
 * To configure this accessor, just fill in [dataSourceConfig] properly and then call [init] once per JVM. When the database services
 * are no longer needed, call [destroy] to release all JDBC connections and close the pool.
 *
 * If you're using a customized [DatabaseAccessor], you don't have to fill in the [dataSourceConfig]. Just set proper [databaseAccessorProvider]
 * and then call [init].
 */
object VokOrm {
    private val logger = LoggerFactory.getLogger(VokOrm::class.java)

    /**
     * First, fill in [dataSourceConfig] properly and then call this function once per JVM.
     */
    fun init() {
        databaseAccessor = databaseAccessorProvider()
    }

    /**
     * Closes the current [databaseAccessor]. Does nothing if [databaseAccessor] is null.
     */
    fun destroy() {
        databaseAccessor?.closeQuietly()
        databaseAccessor = null
    }

    @Volatile
    var databaseAccessorProvider: ()->DatabaseAccessor = {
        check(!dataSourceConfig.jdbcUrl.isNullOrBlank()) { "Please set your database JDBC url, username and password into the VaadinOnKotlin.dataSourceConfig field prior initializing VoK. " }
        HikariDataSourceAccessor(HikariDataSource(dataSourceConfig))
    }

    /**
     * After [init] has been called, this will be filled in. Used to run blocks in a transaction. Closed in [destroy].
     */
    @Volatile
    var databaseAccessor: DatabaseAccessor? = null

    val dataSource: DataSource? get() = databaseAccessor?.dataSource

    /**
     * Configure this before calling [init]. At minimum you need to set [HikariConfig.dataSource], or
     * [HikariConfig.driverClassName], [HikariConfig.jdbcUrl], [HikariConfig.username] and [HikariConfig.password].
     *
     * Only used by the [HikariDataSourceAccessor] - if you are using your own custom [DatabaseAccessor] you don't have to fill in anything here.
     */
    val dataSourceConfig = HikariConfig()

    /**
     * The validator used by [Entity.validate]. By default tries to build the default validation factory; if there is no provider, a no-op
     * validator is used instead.
     */
    var validator: Validator = try {
        Validation.buildDefaultValidatorFactory().validator
    } catch (ex: NoProviderFoundException) {
        logger.info("JSR 303 Validator Provider was not found on your classpath, disabling entity validation")
        logger.debug("The Validator Provider stacktrace follows", ex)
        NoopValidator
    }
}

/**
 * Provides access to a single JDBC connection and its [Connection], and several utility methods.
 *
 * The [db] function executes block in context of this class.
 * @property con the reference to the [Sql2o](https://www.sql2o.org)'s [Connection]. Typically you'd want to call one of the `createQuery()`
 * methods on the connection.
 * @property jdbcConnection the old-school, underlying JDBC connection.
 */
class PersistenceContext(val con: Connection) : Closeable {
    /**
     * The underlying JDBC connection.
     */
    val jdbcConnection: java.sql.Connection get() = con.jdbcConnection

    override fun close() {
        con.close()
    }
}

/**
 * Makes sure given block is executed in a DB transaction. When the block finishes normally, the transaction commits;
 * if the block throws any exception, the transaction is rolled back.
 *
 * Example of use: `db { con.query() }`
 * @param block the block to run in the transaction. Builder-style provides helpful methods and values, e.g. [PersistenceContext.con]
 */
fun <R> db(block: PersistenceContext.()->R): R {
    val accessor = checkNotNull(VokOrm.databaseAccessor) { "The VokOrm.databaseAccessor has not yet been initialized. Please call VokOrm.init()" }
    return accessor.runInTransaction(block)
}
