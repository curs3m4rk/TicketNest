package com.ticketnest;

import com.ticketnest.repository.ShowRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class TicketnestApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(TicketnestApplication.class, args);
    }

    @Bean
    CommandLineRunner testShowQuery(ShowRepository showRepository) {
        return args -> {
            var shows = showRepository.findShowsByCityAndDate(
                    "Bangalore",
                    java.time.Instant.now(),
                    java.time.Instant.now().plusSeconds(86400)
            );

            shows.forEach(show ->
                    System.out.println(
                            "Found show: " + show.getTitle()
                    )
            );
        };
    }
}