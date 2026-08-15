package com.ticketnest.show;

import com.ticketnest.show.dto.ShowResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    // Returns all shows with basic venue information.
    @GetMapping
    public List<ShowResponse> getShows() {
        return showService.getAllShows();
    }

    // Returns a single show by ID.
    @GetMapping("/{id}")
    public ShowResponse getShow(@PathVariable UUID id) {
        return showService.getShow(id);
    }
}