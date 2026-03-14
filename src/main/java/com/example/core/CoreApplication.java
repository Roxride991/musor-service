package com.example.core;

import com.example.core.config.EnvFileLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CoreApplication {

	public static void main(String[] args) {
		EnvFileLoader.loadDotEnv();
		SpringApplication.run(CoreApplication.class, args);
	}

}
