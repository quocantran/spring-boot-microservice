package com.moviebooking.auth.service;

import com.moviebooking.auth.dto.AuthResponse;
import com.moviebooking.auth.dto.LoginDto;
import com.moviebooking.auth.dto.RegisterDto;
import com.moviebooking.auth.dto.UserDto;

public interface AuthService {

    AuthResponse login(LoginDto dto);

    AuthResponse register(RegisterDto dto);

    UserDto getProfile(String userId);
}
