package com.moviebooking.auth.controller;

import com.moviebooking.auth.dto.AuthResponse;
import com.moviebooking.auth.dto.LoginDto;
import com.moviebooking.auth.dto.RegisterDto;
import com.moviebooking.auth.dto.UserDto;
import com.moviebooking.auth.service.AuthService;
import com.moviebooking.common.auth.Authenticated;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginDto dto) {
        AuthResponse response = authService.login(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterDto dto) {
        AuthResponse response = authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Authenticated
    @GetMapping("/auth/me")
    public ResponseEntity<UserDto> getProfile(HttpServletRequest request) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        UserDto profile = authService.getProfile(user.getSub());
        return ResponseEntity.ok(profile);
    }
}
