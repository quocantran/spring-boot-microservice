package com.moviebooking.auth.service.impl;

import com.moviebooking.auth.dto.AuthResponse;
import com.moviebooking.auth.dto.LoginDto;
import com.moviebooking.auth.dto.RegisterDto;
import com.moviebooking.auth.dto.UserDto;
import com.moviebooking.auth.entity.UserEntity;
import com.moviebooking.auth.entity.UserRole;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.auth.service.AuthService;
import com.moviebooking.common.auth.JwtTokenProvider;
import com.moviebooking.common.exception.CustomExceptions.ConflictException;
import com.moviebooking.common.exception.CustomExceptions.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${payment-service.url:http://localhost:5004}")
    private String paymentServiceUrl;

    private static final String DEFAULT_PASSWORD = "123456";

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationBootstrap() {
        List<UserEntity> usersWithoutPassword = userRepository.findByPasswordHashIsNull();
        if (!usersWithoutPassword.isEmpty()) {
            log.info("Found {} users without passwordHash. Setting default password...", usersWithoutPassword.size());
            String hashed = passwordEncoder.encode(DEFAULT_PASSWORD);
            for (UserEntity user : usersWithoutPassword) {
                user.setPasswordHash(hashed);
                userRepository.save(user);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginDto dto) {
        UserEntity user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng"));

        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            throw new UnauthorizedException("Tài khoản chưa thiết lập mật khẩu");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email hoặc mật khẩu không đúng");
        }

        String roleStr = user.getRole() != null ? user.getRole().name() : "USER";
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), roleStr);

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(roleStr)
                .build();

        return AuthResponse.builder()
                .accessToken(token)
                .user(userDto)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterDto dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new ConflictException("Email đã được sử dụng");
        }

        String userId = UUID.randomUUID().toString();
        String hashed = passwordEncoder.encode(dto.getPassword());

        UserEntity user = UserEntity.builder()
                .id(userId)
                .name(dto.getName())
                .email(dto.getEmail())
                .passwordHash(hashed)
                .role(UserRole.USER)
                .build();

        userRepository.save(user);

        createWalletForUser(userId);

        String roleStr = user.getRole().name();
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), roleStr);

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(roleStr)
                .build();

        return AuthResponse.builder()
                .accessToken(token)
                .user(userDto)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getProfile(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        String roleStr = user.getRole() != null ? user.getRole().name() : "USER";
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(roleStr)
                .build();
    }

    private void createWalletForUser(String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("userId", userId, "initialBalance", 200000);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(paymentServiceUrl + "/wallets", entity, Map.class);
            log.info("Wallet auto-created for registered userId: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to auto-create wallet for userId: {}. Error: {}", userId, e.getMessage());
        }
    }
}
