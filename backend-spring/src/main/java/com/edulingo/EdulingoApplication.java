package com.edulingo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EdulingoApplication {
    public static void main(String[] args) {
        SpringApplication.run(EdulingoApplication.class, args);
    }
}
