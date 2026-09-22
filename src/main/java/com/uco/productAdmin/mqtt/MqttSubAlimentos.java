package com.uco.productAdmin.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.concurrent.CountDownLatch; // Importante agregar esto

public class MqttSubAlimentos {

    public static void main(String[] args) throws Exception {

        String broker = MqttBrokerConfig.getBrokerUrl();
        String clientId = MqttClient.generateClientId();

        IMqttClient client = new MqttClient(broker, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);

        System.out.println("Conectando al broker...");
        client.connect(options);

        // Definir callback para recibir mensajes
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("Conexión perdida: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                System.out.println("\n--- Nuevo Mensaje Recibido ---");
                System.out.println("Topic: " + topic);
                System.out.println("Contenido: " + new String(message.getPayload()));
                System.out.println("------------------------------");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // No aplica para el consumidor
            }
        });

        // Suscribirse a un tópico
        String topic = "alimentos";
        client.subscribe(topic, 1); // QoS 1

        System.out.println("Suscrito al tópico: " + topic);
        System.out.println("Esperando mensajes de forma indefinida... (Presiona Ctrl+C en la terminal para apagarlo)");

        // REEMPLAZO DEL THREAD.SLEEP:
        // Esto crea un "candado" que mantendrá el programa corriendo para siempre,
        // permitiendo que el MqttCallback escuche infinitos mensajes.
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }
}