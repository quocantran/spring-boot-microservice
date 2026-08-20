package com.moviebooking.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import com.moviebooking.payment.entity.TopupEntity;
import com.moviebooking.payment.entity.TopupStatus;
import com.moviebooking.payment.service.TopupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TopupControllerTest {

    @Mock
    private TopupService topupService;

    @InjectMocks
    private TopupController topupController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(topupController, "frontendPort", "3000");
        mockMvc = MockMvcBuilders.standaloneSetup(topupController).build();
    }

    @Test
    @DisplayName("POST /topup - Should create topup link for authenticated user")
    void should_CreateTopup_when_Authenticated() throws Exception {
        JwtPayload user = JwtPayload.builder().sub("user-topup-1").email("user@example.com").role("USER").name("User Topup").build();
        Map<String, Object> serviceResult = Map.of(
                "checkoutUrl", "https://pay.payos.vn/web/123",
                "orderCode", 123456789L
        );
        when(topupService.createTopup(eq("user-topup-1"), eq(BigDecimal.valueOf(50000.0))))
                .thenReturn(serviceResult);

        Map<String, Object> body = Map.of("amount", 50000.0);

        mockMvc.perform(post("/topup")
                        .requestAttr(JwtAuthFilter.USER_ATTRIBUTE, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo link thanh toán thành công"))
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://pay.payos.vn/web/123"))
                .andExpect(jsonPath("$.data.orderCode").value(123456789L));
    }

    @Test
    @DisplayName("GET /topup/verify/{orderCode} - Should redirect to frontend success on PAID")
    void should_RedirectToSuccess_when_VerifyPaid() throws Exception {
        when(topupService.verifyTopup(123456L)).thenReturn("PAID");

        mockMvc.perform(get("/topup/verify/123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/wallet?topup=success&orderCode=123456"));
    }

    @Test
    @DisplayName("GET /topup/verify/{orderCode} - Should redirect to cancelled on CANCELLED")
    void should_RedirectToCancelled_when_VerifyCancelled() throws Exception {
        when(topupService.verifyTopup(123456L)).thenReturn("CANCELLED");

        mockMvc.perform(get("/topup/verify/123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/wallet?topup=cancelled&orderCode=123456"));
    }

    @Test
    @DisplayName("GET /topup/verify/{orderCode} - Should redirect to expired on EXPIRED")
    void should_RedirectToExpired_when_VerifyExpired() throws Exception {
        when(topupService.verifyTopup(123456L)).thenReturn("EXPIRED");

        mockMvc.perform(get("/topup/verify/123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/wallet?topup=expired&orderCode=123456"));
    }

    @Test
    @DisplayName("GET /topup/verify/{orderCode} - Should redirect to failed on exception")
    void should_RedirectToFailed_when_VerifyThrowsException() throws Exception {
        when(topupService.verifyTopup(999999L)).thenThrow(new RuntimeException("Verify error"));

        mockMvc.perform(get("/topup/verify/999999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/wallet?topup=failed&orderCode=999999"));
    }

    @Test
    @DisplayName("POST /topup/webhook - Should process webhook and return success true")
    void should_HandleWebhook_successfully() throws Exception {
        Map<String, Object> webhookBody = Map.of(
                "data", Map.of("orderCode", 123456L, "amount", 50000),
                "signature", "valid-signature"
        );

        mockMvc.perform(post("/topup/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(topupService).handleWebhook(any());
    }

    @Test
    @DisplayName("GET /topup/history - Should return user's topup history")
    void should_GetTopupHistory_when_Authenticated() throws Exception {
        JwtPayload user = JwtPayload.builder().sub("user-hist").email("user@example.com").role("USER").name("User Hist").build();
        TopupEntity item = TopupEntity.builder()
                .orderCode(111L)
                .amount(BigDecimal.valueOf(100000.0))
                .status(TopupStatus.PAID)
                .build();
        when(topupService.getTopupHistory("user-hist")).thenReturn(List.of(item));

        mockMvc.perform(get("/topup/history")
                        .requestAttr(JwtAuthFilter.USER_ATTRIBUTE, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lịch sử nạp tiền"))
                .andExpect(jsonPath("$.data[0].orderCode").value(111L));
    }

    @Test
    @DisplayName("DELETE /topup/{orderCode} - Should cancel topup for user")
    void should_CancelTopup_when_Authenticated() throws Exception {
        JwtPayload user = JwtPayload.builder().sub("user-cancel").email("user@example.com").role("USER").name("User Cancel").build();

        mockMvc.perform(delete("/topup/888888")
                        .requestAttr(JwtAuthFilter.USER_ATTRIBUTE, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Huỷ giao dịch thành công"));

        verify(topupService).cancelTopup(888888L, "user-cancel");
    }
}
