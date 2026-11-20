package com.studenttask;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StudentTaskApplication {

    public static void main(String[] args) {
        // All timestamps are stored and processed in UTC; zone conversion happens only at the edges.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(StudentTaskApplication.class, args);
    }
}
