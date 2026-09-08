package com.ticketnest.show;

import com.ticketnest.common.dto.PageResponse;
import com.ticketnest.config.ApiPaths;
import com.ticketnest.show.dto.ShowRequest;
import com.ticketnest.show.dto.ShowResponse;
import com.ticketnest.show.dto.ShowFilter;
import com.ticketnest.show.dto.ShowInventoryRequest;
import com.ticketnest.show.dto.ShowInventoryResponse;
import com.ticketnest.show.dto.ShowSeatResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for Show CRUD.
 * Endpoints: GET/POST /api/v1/shows, GET/PUT/DELETE /api/v1/shows/{id}
 * Returns ShowResponse with venue summary and seat tiers for catalog display.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/shows")
public class ShowController {

    private final ShowService showService;
    private final ShowInventoryService showInventoryService;

    public ShowController(ShowService showService, ShowInventoryService showInventoryService) {
        this.showService = showService;
        this.showInventoryService = showInventoryService;
    }

    /**
     * Lists shows with optional city, genre, and start-time filters.
     * Default: page 0, size 20, sorted by startTime ASC (upcoming first).
     * Query params: city, genre, from, to, page, size, sort.
     * Returns PageResponse with pagination metadata.
     */
    @GetMapping
    public PageResponse<ShowResponse> getShows(
            @Valid @ModelAttribute ShowFilter filter,
            @PageableDefault(size = 20, sort = "startTime", direction = org.springframework.data.domain.Sort.Direction.ASC)
            Pageable pageable) {
        return showService.getAllShows(filter, pageable);
    }

    /** Gets a single show by ID with venue and seat tiers. */
    @GetMapping("/{id}")
    public ShowResponse getShow(@PathVariable UUID id) {
        return showService.getShow(id);
    }

    @PostMapping("/{id}/inventory")
    @PreAuthorize("hasAuthority('SHOW_MANAGE')")
    public ResponseEntity<ShowInventoryResponse> initializeInventory(
            @PathVariable UUID id, @Valid @RequestBody ShowInventoryRequest request) {
        ShowInventoryResponse response = showInventoryService.initialize(id, request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/shows/" + id + "/seats")).body(response);
    }

    @GetMapping("/{id}/seats")
    public java.util.List<ShowSeatResponse> getSeats(@PathVariable UUID id) {
        return showInventoryService.getAvailability(id);
    }

    /** Creates a new show. Returns 201 with created show. */
    @PostMapping
    @PreAuthorize("hasAuthority('SHOW_MANAGE')")
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody ShowRequest request) {
        ShowResponse response = showService.createShow(request);
        return ResponseEntity.status(201).body(response);
    }

    /** Updates an existing show (including venue reassignment). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SHOW_MANAGE')")
    public ShowResponse updateShow(@PathVariable UUID id, @Valid @RequestBody ShowRequest request) {
        return showService.updateShow(id, request);
    }

    /** Deletes a show. Returns 204 no content. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SHOW_MANAGE')")
    public ResponseEntity<Void> deleteShow(@PathVariable UUID id) {
        showService.deleteShow(id);
        return ResponseEntity.noContent().build();
    }
}
