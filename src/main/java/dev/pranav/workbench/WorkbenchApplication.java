package dev.pranav.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class WorkbenchApplication {
    public static void main(String[] args) { SpringApplication.run(WorkbenchApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
