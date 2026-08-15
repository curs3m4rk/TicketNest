package com.ticketnest.show;

import com.ticketnest.entity.Show;
import com.ticketnest.repository.ShowRepository;
import com.ticketnest.show.dto.ShowResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;

    public ShowService(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    public List<ShowResponse> getAllShows() {
        return showRepository.findAllWithVenue()
                .stream()
                .map(show -> new ShowResponse(
                        show.getId(),
                        show.getTitle(),
                        show.getGenre(),
                        show.getStartTime(),
                        show.getStatus(),
                        new ShowResponse.VenueSummary(
                                show.getVenue().getId(),
                                show.getVenue().getName(),
                                show.getVenue().getCity(),
                                show.getVenue().getAddress()
                        )
                ))
                .toList();
    }

    public ShowResponse getShow(UUID id) {
        Show show = showRepository.findByIdWithVenue(id)
                .orElseThrow(() -> new RuntimeException("Show with id " + id + " not found"));

        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getGenre(),
                show.getStartTime(),
                show.getStatus(),
                new ShowResponse.VenueSummary(
                        show.getVenue().getId(),
                        show.getVenue().getName(),
                        show.getVenue().getCity(),
                        show.getVenue().getAddress()
                )
        );
    }
}