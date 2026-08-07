package com.moviebooking.seat.controller;

import com.moviebooking.seat.dto.GenerateSeatsRequest;
import com.moviebooking.seat.dto.GenerateSeatsResponse;
import com.moviebooking.seat.entity.SeatEntity;
import com.moviebooking.seat.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/seats")
    public ResponseEntity<List<SeatEntity>> getSeatsByShowtime(@RequestParam("showtimeId") String showtimeId) {
        List<SeatEntity> seats = seatService.findByShowtimeId(showtimeId);
        return ResponseEntity.ok(seats);
    }

    @PostMapping("/seats/generate")
    public ResponseEntity<GenerateSeatsResponse> generateSeats(@Valid @RequestBody GenerateSeatsRequest body) {
        List<SeatEntity> seats = seatService.generateSeatsForShowtime(
                body.getShowtimeId(),
                body.getRows(),
                body.getCols()
        );
        return ResponseEntity.ok(GenerateSeatsResponse.builder()
                .generated(seats.size())
                .showtimeId(body.getShowtimeId())
                .build());
    }
}
