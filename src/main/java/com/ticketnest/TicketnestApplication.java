package com.ticketnest;

import com.ticketnest.repository.ShowRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@SpringBootApplication
public class TicketnestApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketnestApplication.class, args);
	}

	@Bean
	CommandLineRunner testShowQuery(ShowRepository showRepository) {
		return args -> {
			ZoneId zone = ZoneId.of("Asia/Kolkata");
			
			Instant start = LocalDate
					.of(2026, 8, 16)
					.atStartOfDay(zone)
					.toInstant();

			Instant end = LocalDate
					.of(2026, 8, 17)
					.atStartOfDay(zone)
					.toInstant();

			var shows = showRepository.findShowsByCityAndDate(
					"Bangalore",
					start,
					end
			);

			shows.forEach(show ->
					System.out.println(
							"Found show: " + show.getTitle()
					)
			);
		};
	}
}