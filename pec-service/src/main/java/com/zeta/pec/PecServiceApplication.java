package com.zeta.pec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PecServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PecServiceApplication.class, args);
    }
}
