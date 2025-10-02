package com.artemis.demo;

import jakarta.jms.Message;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class Listener {

    /*
    A durable subscription to this particular topic causes unsuccessful connection attempts
    to receive no CONNACK on version 2.42.0.
     */
    @JmsListener(destination = "activemq.notifications", containerFactory = "singleConnectionFactory")
    public void onMessage(Message message) {
    }
}
