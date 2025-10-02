package com.artemis.demo;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.activemq.ArtemisContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MqttConnAckTest {
    private static final String CLIENT_ID = MqttClient.generateClientId();
    private static final String MQTT_URL = "tcp://localhost:%d";
    private static final String USERNAME = "wronguser";
    private static final String PASSWORD = "wrongpassword";

    /*
    There are 10 attempts because while this behaviour hasn't been observed with the Docker container as yet,
    in the locally installed Artemis some unsuccessful connection attempts end up acknowledged, at a ratio of
     about 1 to 9 (1 acknowledged, 9 not).
     */
    private static final int TOTAL_CONNECTION_ATTEMPTS = 10;
    private static final int DELAY_BETWEEN_ATTEMPTS = 200;
    
    private static int successfulConnections = 0;
    private static int failedConnectionsWithAck = 0;
    private static int failedConnectionsWithoutAck = 0;

    /*
    This test specifically fails with the ActiveMQ Artemis 2.42.0. When set back to 2.41.0, CONNACK is received
    on every unsuccessful connection attempt. The attempt is unsuccessful because of the wrong user credentials
    instead of the default artemis/artemis.
     */
    private static final ArtemisContainer artemis = new ArtemisContainer("apache/activemq-artemis:2.42.0-alpine")
            .withExposedPorts(61616, 1883);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        artemis.start();
        registry.add("spring.artemis.broker-url=", () -> MQTT_URL.formatted(artemis.getMappedPort(61616)));
    }

    @AfterAll
    static void clean() {
        artemis.stop();
    }

    @Test
    void testMqttConnectionAcknowledgment() throws Exception {
        System.out.println("Starting MQTT Connection Acknowledgment Test...");
        System.out.println("Total connection attempts: " + TOTAL_CONNECTION_ATTEMPTS + "\n");

        for (int i = 0; i < TOTAL_CONNECTION_ATTEMPTS; i++) {
            System.out.println("--- Attempt " + (i + 1) + " of " + TOTAL_CONNECTION_ATTEMPTS + " ---");
            testSingleConnection(MQTT_URL.formatted(artemis.getMappedPort(1883)));

            if (i < TOTAL_CONNECTION_ATTEMPTS - 1) {
                Thread.sleep(DELAY_BETWEEN_ATTEMPTS);
            }
        }
        
        printTestSummary();
        
        int totalProcessed = successfulConnections + failedConnectionsWithAck + failedConnectionsWithoutAck;
        assertEquals(TOTAL_CONNECTION_ATTEMPTS, totalProcessed, "All connection attempts should be accounted for");
        assertEquals(0, failedConnectionsWithoutAck, "There should be no unacked failed connect attempts," +
                " but instead there are: " + failedConnectionsWithoutAck);
    }

    private void testSingleConnection(String brokerUrl) throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();

        MqttClient client = null;
        try {
            client = new MqttClient(brokerUrl, CLIENT_ID, persistence);

            final MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(USERNAME);
            options.setPassword(PASSWORD.toCharArray());
            options.setCleanSession(true);

            client.connect(options);

            if (client.isConnected()) {
                successfulConnections++;
                System.out.println("Result: SUCCESS - Connected successfully.");
                client.disconnect();
            }
            client.close();

        } catch (MqttException me) {
            int reasonCode = me.getReasonCode();

            String reasonDescription;
            boolean isAckFailure = false;

            switch (reasonCode) {
                case MqttException.REASON_CODE_FAILED_AUTHENTICATION:
                    reasonDescription = "Failed Authentication (Bad Username/Password)";
                    isAckFailure = true;
                    break;
                case MqttException.REASON_CODE_NOT_AUTHORIZED:
                    reasonDescription = "Not Authorized";
                    isAckFailure = true;
                    break;
                case MqttException.REASON_CODE_CLIENT_TIMEOUT:
                    reasonDescription = "Client Timeout (No CONNACK Received)";
                    break;
                case MqttException.REASON_CODE_CONNECTION_LOST:
                    reasonDescription = "Connection Lost (No CONNACK Received)";
                    break;
                case MqttException.REASON_CODE_BROKER_UNAVAILABLE:
                    reasonDescription = "Broker Unavailable (No CONNACK Received, but trivially)";
                    break;
                default:
                    reasonDescription = "Other Error (" + reasonCode + ")";
                    if (me.getMessage() != null && (
                            me.getMessage().toLowerCase().contains("unable to connect") ||
                                    me.getMessage().toLowerCase().contains("connection refused") ||
                                    me.getMessage().contains("32103"))) {
                        System.out.println(">>> TCP-level connection failure - likely NO CONNACK was received.");
                    }
                    break;
            }

            if (isAckFailure) {
                failedConnectionsWithAck++;
            } else {
                failedConnectionsWithoutAck++;
            }

            System.out.println("Result: FAILED - " + reasonDescription);
            System.out.println("Client ID: " + CLIENT_ID);
            System.out.println("Exception Message: " + me.getMessage());
            System.out.println("Reason Code: " + reasonCode);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private void printTestSummary() {
        System.out.println("\n========== TEST SUMMARY ==========");
        System.out.println("Successful connections: " + successfulConnections);
        System.out.println("Failed connections WITH CONNACK (explicit refusal): " + failedConnectionsWithAck);
        System.out.println("Failed connections WITHOUT CONNACK (no response): " + failedConnectionsWithoutAck);
        
        if (failedConnectionsWithoutAck > 0) {
            System.out.println("\n>>> ISSUE CONFIRMED: Some connection attempts received no CONNACK.");
        } else {
            System.out.println("\n>>> No missing CONNACK packets detected in this test run.");
        }
    }

}