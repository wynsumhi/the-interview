package com.theinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class TheInterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(TheInterviewApplication.class, args);
    }

}
