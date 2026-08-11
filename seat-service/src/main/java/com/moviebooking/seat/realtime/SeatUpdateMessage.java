package com.moviebooking.seat.realtime;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatUpdateMessage {
    private String showtimeId;
    private List<String> seatIds;
    private List<String> seatNumbers;
    private String status;
}
