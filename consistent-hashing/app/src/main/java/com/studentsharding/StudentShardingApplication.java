package com.studentsharding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StudentShardingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentShardingApplication.class, args);
    }
}
