package com.gymfit.booking;

import com.gymfit.booking.dto.*;
import com.gymfit.common.security.SecurityContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final SecurityContextService securityContextService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BookingResponse> list() {
        return bookingService.list(
                securityContextService.principal()
        );
    }

    @GetMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public List<AvailabilitySlotResponse> availability(
            @RequestParam Long facilityId,
            @RequestParam LocalDate date
    ) {
        return bookingService.availability(
                securityContextService.principal(),
                facilityId,
                date
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BookingResponse get(
            @PathVariable Long id
    ) {
        return bookingService.get(
                securityContextService.principal(),
                id
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public BookingResponse create(
            @Valid @RequestBody BookingCreateRequest request
    ) {
        return bookingService.create(
                securityContextService.principal(),
                request
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public BookingResponse cancel(
            @PathVariable Long id,
            @Valid @RequestBody BookingCancelRequest request
    ) {
        return bookingService.cancel(
                securityContextService.principal(),
                id,
                request
        );
    }
}