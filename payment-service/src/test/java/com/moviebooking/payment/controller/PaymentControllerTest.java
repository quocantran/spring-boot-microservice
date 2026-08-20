package com.moviebooking.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import com.moviebooking.payment.entity.WalletEntity;
import com.moviebooking.payment.realtime.WalletRealtimePublisher;
import com.moviebooking.payment.realtime.WalletSseManager;
import com.moviebooking.payment.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletSseManager walletSseManager;

    @Mock
    private WalletRealtimePublisher walletRealtimePublisher;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController).build();
    }

    @Test
    @DisplayName("GET /wallets/me - Should return wallet balance for authenticated user")
    void should_ReturnMyWallet_when_Authenticated() throws Exception {
        JwtPayload user = JwtPayload.builder().sub("user-123").email("user@example.com").role("USER").name("User Name").build();
        WalletEntity wallet = WalletEntity.builder().userId("user-123").balance(BigDecimal.valueOf(250000.0)).build();
        when(walletRepository.findById("user-123")).thenReturn(Optional.of(wallet));

        mockMvc.perform(get("/wallets/me")
                        .requestAttr(JwtAuthFilter.USER_ATTRIBUTE, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.balance").value(250000.0));
    }

    @Test
    @DisplayName("GET /wallets/me - Should return 0.0 balance if wallet not found")
    void should_ReturnZeroBalance_when_WalletNotFound() throws Exception {
        JwtPayload user = JwtPayload.builder().sub("user-new").email("new@example.com").role("USER").name("New User").build();
        when(walletRepository.findById("user-new")).thenReturn(Optional.empty());

        mockMvc.perform(get("/wallets/me")
                        .requestAttr(JwtAuthFilter.USER_ATTRIBUTE, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-new"))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    @DisplayName("GET /wallets/stream - Should create SSE emitter for param userId or header or userAttr")
    void should_CreateSseEmitter_when_Requested() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(walletSseManager.createUserEmitter("user-param")).thenReturn(emitter);

        mockMvc.perform(get("/wallets/stream")
                        .param("userId", "user-param"))
                .andExpect(status().isOk());

        verify(walletSseManager).createUserEmitter("user-param");
    }

    @Test
    @DisplayName("GET /wallets/stream - Should fallback to header X-User-Id when param is absent")
    void should_FallbackToHeader_when_ParamAbsent() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(walletSseManager.createUserEmitter("user-header")).thenReturn(emitter);

        mockMvc.perform(get("/wallets/stream")
                        .header("X-User-Id", "user-header"))
                .andExpect(status().isOk());

        verify(walletSseManager).createUserEmitter("user-header");
    }

    @Test
    @DisplayName("POST /wallets - Should return existing wallet if already exists")
    void should_ReturnExistingWallet_when_AlreadyExists() throws Exception {
        WalletEntity existing = WalletEntity.builder().userId("user-exists").balance(BigDecimal.valueOf(100000.0)).build();
        when(walletRepository.findById("user-exists")).thenReturn(Optional.of(existing));

        Map<String, Object> body = Map.of("userId", "user-exists");

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-exists"))
                .andExpect(jsonPath("$.balance").value(100000.0))
                .andExpect(jsonPath("$.created").value(false));

        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST /wallets - Should create new wallet with initial balance")
    void should_CreateNewWallet_when_NotExists() throws Exception {
        when(walletRepository.findById("user-created")).thenReturn(Optional.empty());

        Map<String, Object> body = Map.of("userId", "user-created", "initialBalance", 300000.0);

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-created"))
                .andExpect(jsonPath("$.balance").value(300000.0))
                .andExpect(jsonPath("$.created").value(true));

        verify(walletRepository).save(any(WalletEntity.class));
        verify(walletRealtimePublisher).publishWalletUpdate("user-created", 300000.0);
    }
}
