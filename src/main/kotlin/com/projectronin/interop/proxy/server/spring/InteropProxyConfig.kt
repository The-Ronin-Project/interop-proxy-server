package com.projectronin.interop.proxy.server.spring

import org.ktorm.database.Database
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class InteropProxyConfig {
    /**
     * The returns [DataSource] for the interop-queue.  Datasource config need to be prefixed by "spring.queue.datasource".
     * See Also: [Bean], [Qualifier], and [ConfigurationProperties] annotations.
     */
    @Bean
    @Qualifier("queue")
    @ConfigurationProperties(prefix = "spring.queue.datasource")
    fun queueDatasource(): DataSource = DataSourceBuilder.create().build()

    /**
     * The returns [Database] for the interop-queue.
     * See Also: [Bean] and [Qualifier] annotation.
     */
    @Bean
    @Qualifier("queue")
    fun queueDatabase(@Qualifier("queue") queueDatasource: DataSource): Database = Database.connect(queueDatasource)

    /**
     * The returns [DataSource] for the interop-ehr. Datasource config need to be prefixed by "spring.ehr.datasource".
     * See Also: [Bean], [Qualifier], and [ConfigurationProperties] annotations.
     */
    @Bean
    @Qualifier("ehr")
    @ConfigurationProperties(prefix = "spring.ehr.datasource")
    fun ehrDatasource(): DataSource = DataSourceBuilder.create().build()

    /**
     * The returns [Database] for the interop-ehr.
     * See Also: [Bean] and [Qualifier] annotation.
     */
    @Bean
    @Qualifier("ehr")
    fun ehrDatabase(@Qualifier("ehr") ehrDatasource: DataSource): Database = Database.connect(ehrDatasource)
}
