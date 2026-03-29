package com.gacfox.meowclaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MeowclawApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeowclawApplication.class, args);
    }

}
