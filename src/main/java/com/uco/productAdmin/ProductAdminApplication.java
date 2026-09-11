package com.uco.productAdmin;

import com.uco.productAdmin.mqtt.MqttPub;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class ProductAdminApplication {

	public static void main(String[] args) {

		ApplicationContext context = SpringApplication.run(ProductAdminApplication.class, args);

		System.out.println("hola mundo");

	}

}