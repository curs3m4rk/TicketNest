package com.ticketnest.show;

import com.ticketnest.entity.Show;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

final class ShowSpecifications {

    private ShowSpecifications() {}

    static Specification<Show> cityEquals(String city) {
        return (root, query, criteriaBuilder) -> hasText(city)
                ? criteriaBuilder.equal(
                        criteriaBuilder.lower(root.join("venue").get("city")),
                        city.trim().toLowerCase(Locale.ROOT))
                : criteriaBuilder.conjunction();
    }

    static Specification<Show> genreEquals(String genre) {
        return (root, query, criteriaBuilder) -> hasText(genre)
                ? criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("genre")),
                        genre.trim().toLowerCase(Locale.ROOT))
                : criteriaBuilder.conjunction();
    }

    static Specification<Show> startsAtOrAfter(Instant from) {
        return (root, query, criteriaBuilder) -> from == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    static Specification<Show> startsBefore(Instant to) {
        return (root, query, criteriaBuilder) -> to == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.lessThan(root.get("startTime"), to);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
