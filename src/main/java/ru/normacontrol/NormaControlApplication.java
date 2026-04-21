package ru.normacontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NormaControlApplication {

    public static void main(String[] args) {
        SpringApplication.run(NormaControlApplication.class, args);
    }
}
