package com.uco.productAdmin.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MqttPub {

    @Value("${mqtt.broker.url}")
    private String broker;

    public void publicar(String topic, String jsonPayload) {
        try {
            String clientId = MqttClient.generateClientId();
            IMqttClient client = new MqttClient(broker, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            System.out.println("Conectando al broker...");
            client.connect(options);

            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(1);
            message.setRetained(false);

            System.out.println("Publicando JSON en el tópico [" + topic + "]: " + jsonPayload);
            client.publish(topic, message);

            client.disconnect();
            System.out.println("Desconectado del broker.");
        } catch (Exception e) {
            System.err.println("Error publicando en MQTT: " + e.getMessage());
        }
    }
}