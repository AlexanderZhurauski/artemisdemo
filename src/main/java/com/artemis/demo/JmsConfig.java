package com.artemis.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.connection.SingleConnectionFactory;

import java.util.UUID;

@Configuration
@EnableJms
public class JmsConfig {

    @Bean("singleConnectionFactory")
    public JmsListenerContainerFactory<?> singleConnectionFactory(SingleConnectionFactory connectionFactory) {
        connectionFactory.setClientId(UUID.randomUUID().toString());
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSubscriptionDurable(true);
        return factory;
    }
}