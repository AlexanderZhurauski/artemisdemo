package com.artemis.demo;

import jakarta.jms.Message;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class Listener {

    @JmsListener(destination = "activemq.notifications", containerFactory = "singleConnectionFactory")
    public void onMessage(Message message) {
    }
}
