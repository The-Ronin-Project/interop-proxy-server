package com.projectronin.interop.proxy.server.spring

import org.ktorm.database.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class InteropProxyConfig {
    @Bean
    fun database(dataSource: DataSource): Database = Database.connect(dataSource)
}
