package com.artemis.demo;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttConnAckTester {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = MqttClient.generateClientId();
    private static final String USERNAME = "none";
    private static final String PASSWORD = "wrongpassword";

    private static final int CONNECTION_TIMEOUT = 10;
    private static final int KEEP_ALIVE_INTERVAL = 60;
    private static final int TOTAL_CONNECTION_ATTEMPTS = 20;
    private static final int DELAY_BETWEEN_ATTEMPTS = 500;

    private static int successfulConnections = 0;
    private static int failedConnectionsWithAck = 0;

    public static void main(String[] args) {
        System.out.println("Starting MQTT Connection Acknowledgment Test...");
        System.out.println("Target Broker: " + BROKER_URL);
        System.out.println("Total connection attempts: " + TOTAL_CONNECTION_ATTEMPTS + "\n");

        for (int i = 0; i < TOTAL_CONNECTION_ATTEMPTS; i++) {
            System.out.println("--- Attempt " + (i + 1) + " of " + TOTAL_CONNECTION_ATTEMPTS + " ---");
            testSingleConnection();
            

            try {
                Thread.sleep(DELAY_BETWEEN_ATTEMPTS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("\n========== TEST SUMMARY ==========");
        System.out.println("Successful connections: " + successfulConnections);
        System.out.println("Failed connections WITH CONNACK (explicit refusal): " + failedConnectionsWithAck);
        System.out.println("Failed connections WITHOUT CONNACK (no response): " + (TOTAL_CONNECTION_ATTEMPTS
                - failedConnectionsWithAck - successfulConnections));
    }

    private static void testSingleConnection() {
        MemoryPersistence persistence = new MemoryPersistence();

        try {
            MqttClient client = new MqttClient(BROKER_URL, CLIENT_ID, persistence);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(USERNAME);
            options.setPassword(PASSWORD.toCharArray());
            options.setConnectionTimeout(CONNECTION_TIMEOUT);
            options.setKeepAliveInterval(KEEP_ALIVE_INTERVAL);
            options.setCleanSession(true);
            options.setAutomaticReconnect(false);

            client.connect(options);

            if (client.isConnected()) {
                successfulConnections++;
                System.out.println("Result: SUCCESS - Connected successfully.");
                client.disconnect();
            }

        } catch (MqttException me) {
            int reasonCode = me.getReasonCode();

            String reasonDescription;
            switch (reasonCode) {
                case MqttException.REASON_CODE_INVALID_PROTOCOL_VERSION:
                    reasonDescription = "Invalid Protocol Version";
                    break;
                case MqttException.REASON_CODE_INVALID_CLIENT_ID:
                    reasonDescription = "Invalid Client Identifier";
                    break;
                case MqttException.REASON_CODE_BROKER_UNAVAILABLE:
                    reasonDescription = "Broker Unavailable";
                    break;
                case MqttException.REASON_CODE_FAILED_AUTHENTICATION:
                    reasonDescription = "Failed Authentication (Bad Username/Password)";
                    failedConnectionsWithAck++;
                    break;
                case MqttException.REASON_CODE_NOT_AUTHORIZED:
                    reasonDescription = "Not Authorized";
                    failedConnectionsWithAck++;
                    break;
                case MqttException.REASON_CODE_CLIENT_TIMEOUT:
                    reasonDescription = "Client Timeout (No CONNACK Received)";
                    break;
                case MqttException.REASON_CODE_CONNECTION_LOST:
                    reasonDescription = "Connection Lost";
                    break;
                default:
                    reasonDescription = "Other Error (" + reasonCode + ")";
                    break;
            }

            System.out.println("Result: FAILED - " + reasonDescription);
            System.out.println("Exception Message: " + me.getMessage());
        }

        System.out.println();
    }

}