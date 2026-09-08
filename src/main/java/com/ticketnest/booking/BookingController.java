package com.ticketnest.booking;

import com.ticketnest.booking.dto.BookingCreateRequest;
import com.ticketnest.booking.dto.BookingResponse;
import com.ticketnest.common.dto.PageResponse;
import com.ticketnest.config.ApiPaths;
import com.ticketnest.entity.BookingStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @Valid @RequestBody BookingCreateRequest request,
                                                   Authentication authentication) {
        BookingService.CreationResult result = bookingService.create(authentication.getName(), idempotencyKey, request);
        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/bookings/" + result.response().id())).body(result.response());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id, Authentication authentication) {
        return bookingService.get(authentication.getName(), id);
    }

    @GetMapping
    public PageResponse<BookingResponse> list(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {
        return bookingService.list(authentication.getName(), status, pageable);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id, Authentication authentication) {
        return bookingService.cancel(authentication.getName(), id);
    }
}
