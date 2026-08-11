package com.moviebooking.seat.controller;

import com.moviebooking.common.constants.SseConstants;
import com.moviebooking.seat.dto.GenerateSeatsRequest;
import com.moviebooking.seat.dto.GenerateSeatsResponse;
import com.moviebooking.seat.entity.SeatEntity;
import com.moviebooking.seat.realtime.SeatSseManager;
import com.moviebooking.seat.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;
    private final SeatSseManager seatSseManager;

    @GetMapping("/seats")
    public ResponseEntity<List<SeatEntity>> getSeatsByShowtime(@RequestParam("showtimeId") String showtimeId) {
        List<SeatEntity> seats = seatService.findByShowtimeId(showtimeId);
        return ResponseEntity.ok(seats);
    }

    /**
     * SSE endpoint for real-time seat status updates.
     * Clients subscribe to a showtime and receive events when seats are confirmed (BOOKED).
     */
    @GetMapping(value = "/seats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeatUpdates(@RequestParam("showtimeId") String showtimeId) {
        // SSE timeout configured via constant
        SseEmitter emitter = new SseEmitter(SseConstants.DEFAULT_SSE_TIMEOUT_MS);

        seatSseManager.addEmitter(showtimeId, emitter);
        log.info("SSE client connected for showtimeId: {}", showtimeId);

        // Send initial heartbeat to confirm connection
        try {
            emitter.send(SseEmitter.event()
                    .name(SseConstants.EVENT_CONNECTED)
                    .data("{\"message\":\"Connected to seat updates\",\"showtimeId\":\"" + showtimeId + "\"}"));
        } catch (Exception e) {
            log.warn("Failed to send initial SSE event for showtimeId: {}", showtimeId);
        }

        return emitter;
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

