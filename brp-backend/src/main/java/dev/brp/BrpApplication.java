package dev.brp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BrpApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrpApplication.class, args);
    }
}
