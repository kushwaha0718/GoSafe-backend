package com.gosafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GoSafeApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoSafeApplication.class, args);
        System.out.println("\n🛡  GoSafe API → http://localhost:3001\n");
    }
}
