package com.moviebooking.booking.controller;

import com.moviebooking.booking.dto.CreateBookingRequest;
import com.moviebooking.booking.entity.BookingEntity;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.auth.Authenticated;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

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
}
