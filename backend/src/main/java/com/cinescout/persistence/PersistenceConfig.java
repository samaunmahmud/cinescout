package com.cinescout.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

    @Bean
    BlockingTransactions blockingTransactions(TransactionTemplate tx) {
        return new BlockingTransactions(tx);
    }
}
