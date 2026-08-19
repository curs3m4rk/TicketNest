package com.ticketnest.show;

import com.ticketnest.show.dto.ShowRequest;
import com.ticketnest.show.dto.ShowResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Show CRUD.
 * Endpoints: GET/POST /api/shows, GET/PUT/DELETE /api/shows/{id}
 * Returns ShowResponse with venue summary and seat tiers for catalog display.
 */
@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    /** Lists all shows with venue and seat tiers. */
    @GetMapping
    public List<ShowResponse> getShows() {
        return showService.getAllShows();
    }

    /** Gets a single show by ID with venue and seat tiers. */
    @GetMapping("/{id}")
    public ShowResponse getShow(@PathVariable UUID id) {
        return showService.getShow(id);
    }

    /** Creates a new show. Returns 201 with created show. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody ShowRequest request) {
        ShowResponse response = showService.createShow(request);
        return ResponseEntity.status(201).body(response);
    }

    /** Updates an existing show (including venue reassignment). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ShowResponse updateShow(@PathVariable UUID id, @Valid @RequestBody ShowRequest request) {
        return showService.updateShow(id, request);
    }

    /** Deletes a show. Returns 204 no content. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteShow(@PathVariable UUID id) {
        showService.deleteShow(id);
        return ResponseEntity.noContent().build();
    }
}