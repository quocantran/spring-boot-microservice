package com.moviebooking.booking.controller;

import com.moviebooking.booking.dto.CreateBookingRequest;
import com.moviebooking.booking.entity.BookingEntity;
import com.moviebooking.booking.realtime.BookingSseManager;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.auth.Authenticated;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingSseManager bookingSseManager;

    @Authenticated
    @PostMapping("/bookings")
    public ResponseEntity<BookingEntity> createBooking(
            HttpServletRequest request,
            @Valid @RequestBody CreateBookingRequest body
    ) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        BookingEntity booking = bookingService.createBooking(user.getSub(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @Authenticated
    @GetMapping("/bookings")
    public ResponseEntity<List<BookingEntity>> getMyBookings(HttpServletRequest request) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        List<BookingEntity> bookings = bookingService.getBookingsByUser(user.getSub());
        return ResponseEntity.ok(bookings);
    }

    @Authenticated
    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingEntity> getBookingById(@PathVariable("id") String id) {
        BookingEntity booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(booking);
    }

    @GetMapping(value = "/bookings/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBookingById(@PathVariable("id") String id) {
        return bookingSseManager.createBookingEmitter(id);
    }

    @GetMapping(value = "/bookings/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUserBookings(
            HttpServletRequest request,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        String targetUserId = userId;
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            targetUserId = headerUserId;
        }
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
            if (user != null) {
                targetUserId = user.getSub();
            }
        }
        return bookingSseManager.createUserEmitter(targetUserId);
    }
}
