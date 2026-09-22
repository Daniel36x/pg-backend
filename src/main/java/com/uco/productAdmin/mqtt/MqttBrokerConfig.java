package com.uco.productAdmin.mqtt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class MqttBrokerConfig {

    private static final String PROPERTY_KEY = "mqtt.broker.url";
    private static final String ENV_VAR = "MQTT_BROKER_URL";

    private MqttBrokerConfig() {
    }

    public static String getBrokerUrl() {
        String fromEnv = System.getenv(ENV_VAR);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        Properties properties = new Properties();
        try (InputStream input = MqttBrokerConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IllegalStateException("No se encontró application.properties en el classpath");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + PROPERTY_KEY + " desde application.properties", e);
        }

        String url = properties.getProperty(PROPERTY_KEY);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("La propiedad " + PROPERTY_KEY + " no está definida en application.properties");
        }

        return url;
    }
}
