package com.moviebooking.common.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtPayload {
    private String sub;
    private String email;
    private String name;
    private String role;
    private Long iat;
    private Long exp;
}
