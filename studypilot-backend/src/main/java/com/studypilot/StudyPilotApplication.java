package com.studypilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.studypilot.mapper")
public class StudyPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyPilotApplication.class, args);
    }
}