package com.moviebooking.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.exception.CustomExceptions.BadRequestException;
import com.moviebooking.common.exception.CustomExceptions.NotFoundException;
import com.moviebooking.payment.entity.TopupEntity;
import com.moviebooking.payment.entity.TopupStatus;
import com.moviebooking.payment.entity.WalletEntity;
import com.moviebooking.payment.realtime.WalletRealtimePublisher;
import com.moviebooking.payment.repository.TopupRepository;
import com.moviebooking.payment.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopupServiceImplTest {

    @Mock
    private TopupRepository topupRepository;

    @Mock
    private WalletRepository walletRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WalletRealtimePublisher walletRealtimePublisher;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TopupServiceImpl topupService;

    private static final String CHECKSUM_KEY = "test-checksum-key-fixed-123456";
    private static final String PAYOS_URL = "https://api-merchant.payos.vn";
    private static final String PAYOS_API_KEY = "test-api-key";
    private static final String PAYOS_CLIENT_ID = "test-client-id";
    private static final String GATEWAY_PORT = "8080";
    private static final String FRONTEND_PORT = "3000";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(topupService, "payosChecksumKey", CHECKSUM_KEY);
        ReflectionTestUtils.setField(topupService, "payosUrl", PAYOS_URL);
        ReflectionTestUtils.setField(topupService, "payosApiKey", PAYOS_API_KEY);
        ReflectionTestUtils.setField(topupService, "payosClientId", PAYOS_CLIENT_ID);
        ReflectionTestUtils.setField(topupService, "gatewayPort", GATEWAY_PORT);
        ReflectionTestUtils.setField(topupService, "frontendPort", FRONTEND_PORT);
        ReflectionTestUtils.setField(topupService, "restTemplate", restTemplate);
    }

    private static String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("2.1 HMAC-SHA256 Signature Tests")
    class HmacSignatureTests {

        @Test
        @DisplayName("Case 1: Known test vector verification (Golden test)")
        void should_GenerateCorrectHmacSignature_withKnownTestVector() {
            String input = "amount=50000&cancelUrl=http://localhost:8080/topup/verify/123456789&description=123456789&orderCode=123456789&returnUrl=http://localhost:8080/topup/verify/123456789";
            String secretKey = "test-checksum-key-fixed-123456";
            String expected = computeHmacSha256(input, secretKey);

            String actual = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input);

            assertThat(actual).isEqualTo(expected);
            assertThat(actual).hasSize(64); // SHA-256 in hex is 64 characters
        }

        @Test
        @DisplayName("Case 2: Avalanche effect - amount changes by 1 unit results in completely different signature")
        void should_GenerateDifferentSignature_when_AmountChanges() {
            String input1 = "amount=50000&orderCode=123";
            String input2 = "amount=50001&orderCode=123";

            String sig1 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input1);
            String sig2 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input2);

            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        @DisplayName("Case 3: Avalanche effect - orderCode changes results in different signature")
        void should_GenerateDifferentSignature_when_OrderCodeChanges() {
            String input1 = "amount=50000&orderCode=100001";
            String input2 = "amount=50000&orderCode=100002";

            String sig1 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input1);
            String sig2 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input2);

            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        @DisplayName("Case 4: Secret key changes results in different signature")
        void should_GenerateDifferentSignature_when_SecretKeyChanges() {
            String input = "amount=50000&orderCode=100001";

            String sig1 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input);
            ReflectionTestUtils.setField(topupService, "payosChecksumKey", "different-secret-key");
            String sig2 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input);

            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        @DisplayName("Case 5: Should handle special characters in input")
        void should_HandleSpecialCharactersInDescription() {
            String input = "amount=50000&description=Nạp tiền #123 & test &orderCode=123";

            String sig = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input);

            assertThat(sig).isNotNull().hasSize(64);
        }

        @Test
        @DisplayName("Case 6: Should produce consistent signature when called multiple times (Deterministic)")
        void should_ProduceConsistentSignature_when_CalledMultipleTimes() {
            String input = "amount=50000&orderCode=999";

            String sig1 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input);
            String sig2 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input);
            String sig3 = ReflectionTestUtils.invokeMethod(topupService, "createSignature", input);

            assertThat(sig1).isEqualTo(sig2).isEqualTo(sig3);
        }

        @Test
        @DisplayName("Case 7: Should throw BadRequestException when checksum key is empty")
        void should_ThrowBadRequest_when_ChecksumKeyIsEmpty() {
            ReflectionTestUtils.setField(topupService, "payosChecksumKey", "  ");

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(topupService, "createSignature", "test"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Chưa cấu hình PAYOS_CHECKSUM_KEY");
        }
    }

    @Nested
    @DisplayName("2.2 Create Topup (createTopup)")
    class CreateTopupTests {

        @Test
        @DisplayName("Case 8: Should throw BadRequestException when amount is below 1000")
        void should_ThrowBadRequest_when_AmountBelow1000() {
            assertThatThrownBy(() -> topupService.createTopup("user-1", BigDecimal.valueOf(999)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Số tiền nạp tối thiểu là 1.000 VNĐ");
        }

        @Test
        @DisplayName("Case 9: Should throw BadRequestException when amount is zero")
        void should_ThrowBadRequest_when_AmountIsZero() {
            assertThatThrownBy(() -> topupService.createTopup("user-1", BigDecimal.ZERO))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Số tiền nạp tối thiểu là 1.000 VNĐ");
        }

        @Test
        @DisplayName("Case 10: Should throw BadRequestException when amount is negative")
        void should_ThrowBadRequest_when_AmountIsNegative() {
            assertThatThrownBy(() -> topupService.createTopup("user-1", BigDecimal.valueOf(-50000)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Số tiền nạp tối thiểu là 1.000 VNĐ");
        }

        @Test
        @DisplayName("Case 11: Should accept minimum boundary amount of 1000")
        void should_AcceptMinimumAmount_when_AmountIs1000() {
            // Given
            Map<String, Object> payosData = Map.of(
                    "checkoutUrl", "https://pay.payos.vn/web/123",
                    "paymentLinkId", "link-123"
            );
            Map<String, Object> payosResponseBody = Map.of("data", payosData);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(payosResponseBody, HttpStatus.OK));

            // When
            Map<String, Object> result = topupService.createTopup("user-1", BigDecimal.valueOf(1000));

            // Then
            assertThat(result).containsKey("checkoutUrl");
            assertThat(result).containsKey("orderCode");
            verify(topupRepository).save(any(TopupEntity.class));
        }

        @Test
        @DisplayName("Case 12: Should call PayOS API with correct body and headers")
        void should_CallPayosApiWithCorrectBody_when_CreatingTopup() {
            // Given
            Map<String, Object> payosData = Map.of(
                    "checkoutUrl", "https://pay.payos.vn/web/456",
                    "paymentLinkId", "link-456"
            );
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            // When
            topupService.createTopup("user-test", BigDecimal.valueOf(50000));

            // Then
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(eq(PAYOS_URL + "/v2/payment-requests"), entityCaptor.capture(), eq(Map.class));

            HttpEntity<Map<String, Object>> captured = entityCaptor.getValue();
            HttpHeaders headers = captured.getHeaders();
            assertThat(headers.getFirst("x-api-key")).isEqualTo(PAYOS_API_KEY);
            assertThat(headers.getFirst("x-client-id")).isEqualTo(PAYOS_CLIENT_ID);

            Map<String, Object> body = captured.getBody();
            assertThat(body.get("amount")).isEqualTo(50000);
            assertThat(body).containsKey("signature");
            assertThat(body).containsKey("orderCode");
        }

        @Test
        @DisplayName("Case 13: Should save TopupEntity with PENDING status when PayOS returns success")
        void should_SaveTopupEntityWithPendingStatus_when_PayosReturnsSuccess() {
            // Given
            Map<String, Object> payosData = Map.of(
                    "checkoutUrl", "https://pay.payos.vn/web/789",
                    "paymentLinkId", "link-789"
            );
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            // When
            topupService.createTopup("user-save", BigDecimal.valueOf(100000));

            // Then
            ArgumentCaptor<TopupEntity> captor = ArgumentCaptor.forClass(TopupEntity.class);
            verify(topupRepository).save(captor.capture());
            TopupEntity saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo("user-save");
            assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000));
            assertThat(saved.getStatus()).isEqualTo(TopupStatus.PENDING);
            assertThat(saved.getCheckoutUrl()).isEqualTo("https://pay.payos.vn/web/789");
            assertThat(saved.getPaymentLinkId()).isEqualTo("link-789");
        }

        @Test
        @DisplayName("Case 14: Should return checkoutUrl and orderCode when topup created successfully")
        void should_ReturnCheckoutUrlAndOrderCode_when_TopupCreatedSuccessfully() {
            // Given
            Map<String, Object> payosData = Map.of(
                    "checkoutUrl", "https://pay.payos.vn/web/abc",
                    "paymentLinkId", "link-abc"
            );
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            // When
            Map<String, Object> result = topupService.createTopup("user-ret", BigDecimal.valueOf(20000));

            // Then
            assertThat(result.get("checkoutUrl")).isEqualTo("https://pay.payos.vn/web/abc");
            assertThat(result.get("orderCode")).isNotNull();
        }

        @Test
        @DisplayName("Case 15: Should throw BadRequestException when PayOS returns non-2xx")
        void should_ThrowBadRequest_when_PayosReturnsNon2xx() {
            // Given
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

            // When / Then
            assertThatThrownBy(() -> topupService.createTopup("user-err", BigDecimal.valueOf(50000)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tạo link thanh toán thất bại");
        }

        @Test
        @DisplayName("Case 16: Should throw BadRequestException when PayOS API times out / throws exception")
        void should_ThrowBadRequest_when_PayosApiTimeout() {
            // Given
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("Connection timed out"));

            // When / Then
            assertThatThrownBy(() -> topupService.createTopup("user-timeout", BigDecimal.valueOf(50000)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Connection timed out");
        }
    }

    @Nested
    @DisplayName("2.3 Handle Webhook (handleWebhook)")
    class HandleWebhookTests {

        private Map<String, Object> createValidWebhookData(Long orderCode, int amount, String code, String status) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", amount);
            data.put("code", code);
            data.put("orderCode", orderCode);
            data.put("reference", "REF-12345");
            data.put("status", status);

            // Compute signature according to verifyWebhookSignature
            // keys sorted: amount, code, orderCode, reference, status
            String dataStr = "amount=" + amount + "&code=" + code + "&orderCode=" + orderCode + "&reference=REF-12345&status=" + status;
            String signature = computeHmacSha256(dataStr, CHECKSUM_KEY);

            Map<String, Object> webhook = new LinkedHashMap<>();
            webhook.put("data", data);
            webhook.put("signature", signature);
            return webhook;
        }

        @Test
        @DisplayName("Case 17: Should ignore webhook when data field is null")
        void should_IgnoreWebhook_when_DataIsNull() {
            Map<String, Object> webhook = new HashMap<>();
            webhook.put("data", null);
            webhook.put("signature", "some-sig");

            topupService.handleWebhook(webhook);

            verifyNoInteractions(topupRepository);
        }

        @Test
        @DisplayName("Case 18: Should ignore webhook when orderCode is missing")
        void should_IgnoreWebhook_when_OrderCodeIsNull() {
            Map<String, Object> data = Map.of("amount", 50000);
            Map<String, Object> webhook = Map.of("data", data, "signature", "some-sig");

            topupService.handleWebhook(webhook);

            verifyNoInteractions(topupRepository);
        }

        @Test
        @DisplayName("Case 19: Should ignore webhook when signature is null")
        void should_IgnoreWebhook_when_SignatureIsNull() {
            Map<String, Object> data = Map.of("orderCode", 123456L, "amount", 50000);
            Map<String, Object> webhook = new HashMap<>();
            webhook.put("data", data);
            webhook.put("signature", null);

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).findByOrderCode(anyLong());
        }

        @Test
        @DisplayName("Case 20: Should ignore webhook when signature is invalid")
        void should_IgnoreWebhook_when_SignatureIsInvalid() {
            Map<String, Object> data = Map.of("orderCode", 123456L, "amount", 50000);
            Map<String, Object> webhook = Map.of("data", data, "signature", "invalid-fake-signature");

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).findByOrderCode(anyLong());
        }

        @Test
        @DisplayName("Case 21: Tamper detection - Reject webhook when signature is from amount=100000 but amount tampered to 1000000")
        void should_RejectWebhook_when_SignatureValidButAmountTampered() {
            // Attacker signs for amount=100000
            String originalDataStr = "amount=100000&orderCode=123456";
            String signature = computeHmacSha256(originalDataStr, CHECKSUM_KEY);

            // But sends amount=1000000 with that signature
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", 1000000);
            data.put("orderCode", 123456L);
            Map<String, Object> webhook = Map.of("data", data, "signature", signature);

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).findByOrderCode(anyLong());
        }

        @Test
        @DisplayName("Case 22: Replay attack - Reject webhook when replayed with different orderCode")
        void should_RejectWebhook_when_ReplayedWithOldSignature() {
            String originalDataStr = "amount=50000&orderCode=111111";
            String signature = computeHmacSha256(originalDataStr, CHECKSUM_KEY);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", 50000);
            data.put("orderCode", 222222L); // Different orderCode
            Map<String, Object> webhook = Map.of("data", data, "signature", signature);

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).findByOrderCode(anyLong());
        }

        @Test
        @DisplayName("Case 23: Should verify signature correctly regardless of field ordering")
        void should_VerifySignature_regardless_of_FieldOrdering() {
            Long orderCode = 555555L;
            // Map keys in arbitrary order
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "PAID");
            data.put("amount", 50000);
            data.put("reference", "REF-555");
            data.put("orderCode", orderCode);
            data.put("code", "00");

            // Sorted key string: amount=50000&code=00&orderCode=555555&reference=REF-555&status=PAID
            String dataStr = "amount=50000&code=00&orderCode=555555&reference=REF-555&status=PAID";
            String signature = computeHmacSha256(dataStr, CHECKSUM_KEY);

            Map<String, Object> webhook = Map.of("data", data, "signature", signature);

            TopupEntity topup = TopupEntity.builder()
                    .id("topup-555")
                    .userId("user-555")
                    .orderCode(orderCode)
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));
            when(topupRepository.markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-555", BigDecimal.valueOf(50000))).thenReturn(1);
            when(walletRepository.findById("user-555")).thenReturn(Optional.of(WalletEntity.builder().userId("user-555").balance(BigDecimal.valueOf(50000)).build()));

            topupService.handleWebhook(webhook);

            verify(topupRepository).markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Case 24: Should skip webhook when topup is already PAID (Idempotency)")
        void should_SkipWebhook_when_TopupAlreadyPaid() {
            Long orderCode = 888888L;
            Map<String, Object> webhook = createValidWebhookData(orderCode, 50000, "00", "PAID");

            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-paid")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PAID)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
            verify(walletRepository, never()).creditBalance(anyString(), any());
        }

        @Test
        @DisplayName("Case 25: Should skip webhook when topup is not found in DB")
        void should_SkipWebhook_when_TopupNotFoundInDB() {
            Long orderCode = 999999L;
            Map<String, Object> webhook = createValidWebhookData(orderCode, 50000, "00", "PAID");
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.empty());

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Case 26: Should ignore webhook when payment code is not 00 and status is not PAID")
        void should_IgnoreWebhook_when_PaymentCodeIsNot00() {
            Long orderCode = 777777L;
            Map<String, Object> webhook = createValidWebhookData(orderCode, 50000, "01", "FAILED");

            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-fail-code")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Case 27: Should ignore webhook when amount does not match topup amount")
        void should_IgnoreWebhook_when_AmountMismatch() {
            Long orderCode = 666666L;
            // Webhook says 50000
            Map<String, Object> webhook = createValidWebhookData(orderCode, 50000, "00", "PAID");

            // But DB topup expects 100000
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-mismatch")
                    .amount(BigDecimal.valueOf(100000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            topupService.handleWebhook(webhook);

            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
            verify(walletRepository, never()).creditBalance(anyString(), any());
        }

        @Test
        @DisplayName("Case 28: Should credit wallet when webhook is valid and paid")
        void should_CreditWallet_when_WebhookIsValidAndPaid() {
            Long orderCode = 333333L;
            Map<String, Object> webhook = createValidWebhookData(orderCode, 50000, "00", "PAID");

            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-happy")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));
            when(topupRepository.markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-happy", BigDecimal.valueOf(50000))).thenReturn(1);
            when(walletRepository.findById("user-happy")).thenReturn(Optional.of(WalletEntity.builder().userId("user-happy").balance(BigDecimal.valueOf(250000)).build()));

            topupService.handleWebhook(webhook);

            verify(topupRepository).markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any());
            verify(walletRepository).creditBalance("user-happy", BigDecimal.valueOf(50000));
            verify(walletRealtimePublisher).publishWalletUpdate("user-happy", 250000.0);
        }

        @Test
        @DisplayName("Case 29: Should extract transaction details and save counter account info")
        void should_ExtractTransactionDetails_when_WebhookContainsBankInfo() {
            Long orderCode = 222222L;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", 70000);
            data.put("code", "00");
            data.put("counterAccountBankName", "MBBank");
            data.put("counterAccountName", "NGUYEN VAN A");
            data.put("counterAccountNumber", "0123456789");
            data.put("orderCode", orderCode);
            data.put("reference", "MB123456");
            data.put("status", "PAID");
            data.put("transactionDateTime", "2026-08-20T10:00:00Z");

            String dataStr = "amount=70000&code=00&counterAccountBankName=MBBank&counterAccountName=NGUYEN VAN A&counterAccountNumber=0123456789&orderCode=" + orderCode + "&reference=MB123456&status=PAID&transactionDateTime=2026-08-20T10:00:00Z";
            String signature = computeHmacSha256(dataStr, CHECKSUM_KEY);

            Map<String, Object> webhook = Map.of("data", data, "signature", signature);

            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-bank-info")
                    .amount(BigDecimal.valueOf(70000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));
            when(topupRepository.markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), eq("MB123456"), eq("MBBank"), eq("NGUYEN VAN A"), eq("0123456789"), any(Instant.class)))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-bank-info", BigDecimal.valueOf(70000))).thenReturn(1);

            topupService.handleWebhook(webhook);

            verify(topupRepository).markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), eq("MB123456"), eq("MBBank"), eq("NGUYEN VAN A"), eq("0123456789"), any(Instant.class));
        }

        @Test
        @DisplayName("Case 30: Should credit wallet when status is PAID even if code is missing")
        void should_CreditWallet_when_StatusIsPaidInsteadOfCode00() {
            Long orderCode = 111111L;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", 30000);
            data.put("orderCode", orderCode);
            data.put("status", "PAID");

            String dataStr = "amount=30000&orderCode=" + orderCode + "&status=PAID";
            String signature = computeHmacSha256(dataStr, CHECKSUM_KEY);

            Map<String, Object> webhook = Map.of("data", data, "signature", signature);

            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-status-paid")
                    .amount(BigDecimal.valueOf(30000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));
            when(topupRepository.markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-status-paid", BigDecimal.valueOf(30000))).thenReturn(1);

            topupService.handleWebhook(webhook);

            verify(walletRepository).creditBalance("user-status-paid", BigDecimal.valueOf(30000));
        }
    }

    @Nested
    @DisplayName("2.4 Credit Wallet Atomic Logic (creditWallet)")
    class CreditWalletTests {

        @Test
        @DisplayName("Case 31: Should skip credit wallet when markAsPaid returns 0 (already processed)")
        void should_SkipCreditWallet_when_TopupAlreadyProcessed() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(101L)
                    .userId("user-skip")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.markAsPaid(eq(101L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(0);

            topupService.creditWallet(topup, Collections.emptyMap());

            verify(walletRepository, never()).creditBalance(anyString(), any());
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("Case 32: Should credit existing wallet when wallet exists")
        void should_CreditExistingWallet_when_WalletExists() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(102L)
                    .userId("user-exist")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.markAsPaid(eq(102L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-exist", BigDecimal.valueOf(50000))).thenReturn(1);
            when(walletRepository.findById("user-exist")).thenReturn(Optional.of(WalletEntity.builder().userId("user-exist").balance(BigDecimal.valueOf(150000)).build()));

            topupService.creditWallet(topup, null);

            verify(walletRepository).creditBalance("user-exist", BigDecimal.valueOf(50000));
            verify(walletRepository, never()).save(any(WalletEntity.class));
            verify(walletRealtimePublisher).publishWalletUpdate("user-exist", 150000.0);
        }

        @Test
        @DisplayName("Case 33: Should create new wallet when wallet does not exist")
        void should_CreateNewWallet_when_WalletDoesNotExist() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(103L)
                    .userId("user-new")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.markAsPaid(eq(103L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-new", BigDecimal.valueOf(50000))).thenReturn(0); // Wallet didn't exist
            when(walletRepository.findById("user-new")).thenReturn(Optional.of(WalletEntity.builder().userId("user-new").balance(BigDecimal.valueOf(50000)).build()));

            topupService.creditWallet(topup, null);

            ArgumentCaptor<WalletEntity> captor = ArgumentCaptor.forClass(WalletEntity.class);
            verify(walletRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo("user-new");
            assertThat(captor.getValue().getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }

        @Test
        @DisplayName("Case 34: Should publish SSE update when credit wallet succeeds")
        void should_PublishSSEUpdate_when_CreditWalletSucceeds() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(104L)
                    .userId("user-sse-test")
                    .amount(BigDecimal.valueOf(80000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.markAsPaid(eq(104L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-sse-test", BigDecimal.valueOf(80000))).thenReturn(1);
            when(walletRepository.findById("user-sse-test")).thenReturn(Optional.of(WalletEntity.builder().userId("user-sse-test").balance(BigDecimal.valueOf(380000)).build()));

            topupService.creditWallet(topup, null);

            verify(walletRealtimePublisher).publishWalletUpdate("user-sse-test", 380000.0);
        }

        @Test
        @DisplayName("Case 35: Should parse transactionDateTime when valid ISO-8601")
        void should_ParseTransactionDateTime_when_ValidISO8601() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(105L)
                    .userId("user-iso")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            Map<String, Object> tx = Map.of("transactionDateTime", "2026-08-20T14:30:00Z");

            when(topupRepository.markAsPaid(eq(105L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), eq(Instant.parse("2026-08-20T14:30:00Z"))))
                    .thenReturn(1);

            topupService.creditWallet(topup, tx);

            verify(topupRepository).markAsPaid(eq(105L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), eq(Instant.parse("2026-08-20T14:30:00Z")));
        }

        @Test
        @DisplayName("Case 36: Should fallback to Instant.now() when transactionDateTime is invalid string")
        void should_FallbackToNow_when_TransactionDateTimeInvalid() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(106L)
                    .userId("user-inv-date")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            Map<String, Object> tx = Map.of("transactionDateTime", "not-a-date");

            when(topupRepository.markAsPaid(eq(106L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any(Instant.class)))
                    .thenReturn(1);

            topupService.creditWallet(topup, tx);

            verify(topupRepository).markAsPaid(eq(106L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any(Instant.class));
        }

        @Test
        @DisplayName("Case 37: Should fallback to Instant.now() when transactionDateTime is null")
        void should_FallbackToNow_when_TransactionDateTimeIsNull() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(107L)
                    .userId("user-null-date")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            Map<String, Object> tx = new HashMap<>();
            tx.put("transactionDateTime", null);

            when(topupRepository.markAsPaid(eq(107L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any(Instant.class)))
                    .thenReturn(1);

            topupService.creditWallet(topup, tx);

            verify(topupRepository).markAsPaid(eq(107L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any(Instant.class));
        }

        @Test
        @DisplayName("Case 38: Should handle null transaction details map gracefully")
        void should_HandleNullTransaction_when_NoTransactionDetails() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(108L)
                    .userId("user-no-tx")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();

            when(topupRepository.markAsPaid(eq(108L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), isNull(), isNull(), isNull(), isNull(), any(Instant.class)))
                    .thenReturn(1);

            topupService.creditWallet(topup, null);

            verify(topupRepository).markAsPaid(eq(108L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), isNull(), isNull(), isNull(), isNull(), any(Instant.class));
        }

        @Test
        @DisplayName("Case 39: Concurrent execution - Two webhooks arrive concurrently, only first one credits wallet")
        void should_CreditOnlyOnce_when_TwoWebhooksArriveConcurrently() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(109L)
                    .userId("user-conc")
                    .amount(BigDecimal.valueOf(100000))
                    .status(TopupStatus.PENDING)
                    .build();

            // First invocation returns affected=1, second returns affected=0
            when(topupRepository.markAsPaid(eq(109L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1)
                    .thenReturn(0);
            when(walletRepository.creditBalance("user-conc", BigDecimal.valueOf(100000))).thenReturn(1);
            when(walletRepository.findById("user-conc")).thenReturn(Optional.of(WalletEntity.builder().userId("user-conc").balance(BigDecimal.valueOf(100000)).build()));

            // Webhook 1
            topupService.creditWallet(topup, Collections.emptyMap());
            // Webhook 2
            topupService.creditWallet(topup, Collections.emptyMap());

            // creditBalance must be called EXACTLY ONCE
            verify(walletRepository, times(1)).creditBalance("user-conc", BigDecimal.valueOf(100000));
            verify(walletRealtimePublisher, times(1)).publishWalletUpdate("user-conc", 100000.0);
        }

        @Test
        @DisplayName("Case 40: Concurrent execution with no existing wallet - does not create duplicate wallets")
        void should_NotCreateDuplicateWallet_when_ConcurrentCreditWithNoExistingWallet() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(110L)
                    .userId("user-conc-new")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();

            when(topupRepository.markAsPaid(eq(110L), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1)
                    .thenReturn(0);
            when(walletRepository.creditBalance("user-conc-new", BigDecimal.valueOf(50000))).thenReturn(0);

            topupService.creditWallet(topup, null);
            topupService.creditWallet(topup, null);

            // save wallet must be called EXACTLY ONCE
            verify(walletRepository, times(1)).save(any(WalletEntity.class));
        }
    }

    @Nested
    @DisplayName("2.5 Verify Topup (verifyTopup)")
    class VerifyTopupTests {

        @Test
        @DisplayName("Case 41: Should throw NotFoundException when orderCode does not exist")
        void should_ThrowNotFound_when_OrderCodeDoesNotExist() {
            when(topupRepository.findByOrderCode(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> topupService.verifyTopup(999L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Không tìm thấy giao dịch nạp tiền");
        }

        @Test
        @DisplayName("Case 42: Should return PAID immediately when topup is already PAID without calling PayOS API")
        void should_ReturnPaid_when_TopupAlreadyPaid() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(401L)
                    .status(TopupStatus.PAID)
                    .build();
            when(topupRepository.findByOrderCode(401L)).thenReturn(Optional.of(topup));

            String result = topupService.verifyTopup(401L);

            assertThat(result).isEqualTo("PAID");
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Case 43: Should return CANCELLED immediately when topup is already CANCELLED")
        void should_ReturnCancelled_when_TopupAlreadyCancelled() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(402L)
                    .status(TopupStatus.CANCELLED)
                    .build();
            when(topupRepository.findByOrderCode(402L)).thenReturn(Optional.of(topup));

            String result = topupService.verifyTopup(402L);

            assertThat(result).isEqualTo("CANCELLED");
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Case 44: Should return EXPIRED immediately when topup is already EXPIRED")
        void should_ReturnExpired_when_TopupAlreadyExpired() {
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(403L)
                    .status(TopupStatus.EXPIRED)
                    .build();
            when(topupRepository.findByOrderCode(403L)).thenReturn(Optional.of(topup));

            String result = topupService.verifyTopup(403L);

            assertThat(result).isEqualTo("EXPIRED");
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Case 45: Should sync from PayOS and return PAID when PayOS status is PAID")
        void should_SyncAndReturnPaid_when_PayosStatusIsPaid() {
            Long orderCode = 404L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-sync-paid")
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            Map<String, Object> payosData = Map.of(
                    "status", "PAID",
                    "amount", 50000,
                    "transactions", List.of(Map.of("reference", "REF-404"))
            );
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            when(topupRepository.markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), eq("REF-404"), any(), any(), any(), any()))
                    .thenReturn(1);
            when(walletRepository.creditBalance("user-sync-paid", BigDecimal.valueOf(50000))).thenReturn(1);

            String result = topupService.verifyTopup(orderCode);

            assertThat(result).isEqualTo("PAID");
            verify(topupRepository).markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), eq("REF-404"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Case 46: Should mark topup as CANCELLED when PayOS status is CANCELLED")
        void should_MarkCancelled_when_PayosStatusIsCancelled() {
            Long orderCode = 405L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            Map<String, Object> payosData = Map.of("status", "CANCELLED");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            String result = topupService.verifyTopup(orderCode);

            assertThat(result).isEqualTo("CANCELLED");
            verify(topupRepository).updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.CANCELLED);
        }

        @Test
        @DisplayName("Case 47: Should mark topup as EXPIRED when PayOS status is EXPIRED")
        void should_MarkExpired_when_PayosStatusIsExpired() {
            Long orderCode = 406L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            Map<String, Object> payosData = Map.of("status", "EXPIRED");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            String result = topupService.verifyTopup(orderCode);

            assertThat(result).isEqualTo("EXPIRED");
            verify(topupRepository).updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.EXPIRED);
        }

        @Test
        @DisplayName("Case 48: Should return PENDING when PayOS status is still PENDING")
        void should_ReturnPending_when_PayosStatusIsStillPending() {
            Long orderCode = 407L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            Map<String, Object> payosData = Map.of("status", "PENDING");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            String result = topupService.verifyTopup(orderCode);

            assertThat(result).isEqualTo("PENDING");
            verify(topupRepository, never()).updateStatusByOrderCode(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Case 49: Should throw BadRequestException when PayOS returns amount mismatch during verify")
        void should_ThrowBadRequest_when_PayosVerifyAmountMismatch() {
            Long orderCode = 408L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .amount(BigDecimal.valueOf(50000))
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            Map<String, Object> payosData = Map.of(
                    "status", "PAID",
                    "amount", 100000 // Mismatch: 100k vs 50k
            );
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            assertThatThrownBy(() -> topupService.verifyTopup(orderCode))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Số tiền không khớp");
        }

        @Test
        @DisplayName("Case 50: Should throw BadRequestException when PayOS API is unavailable during verify")
        void should_ThrowBadRequest_when_PayosApiUnavailable() {
            Long orderCode = 409L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("PayOS down"));

            assertThatThrownBy(() -> topupService.verifyTopup(orderCode))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không thể kiểm tra trạng thái thanh toán");
        }
    }

    @Nested
    @DisplayName("2.6 Topup History (getTopupHistory)")
    class TopupHistoryTests {

        @Test
        @DisplayName("Case 51: Should return empty list when user has no topup history")
        void should_ReturnEmptyList_when_UserHasNoTopupHistory() {
            when(topupRepository.findByUserIdOrderByCreatedAtDesc("user-empty")).thenReturn(Collections.emptyList());

            List<TopupEntity> result = topupService.getTopupHistory("user-empty");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Case 52: Should return history directly without calling PayOS when all topups are settled")
        void should_ReturnHistoryDirectly_when_NoPendingTopups() {
            TopupEntity paid = TopupEntity.builder().orderCode(501L).status(TopupStatus.PAID).build();
            TopupEntity cancelled = TopupEntity.builder().orderCode(502L).status(TopupStatus.CANCELLED).build();
            when(topupRepository.findByUserIdOrderByCreatedAtDesc("user-settled")).thenReturn(List.of(paid, cancelled));

            List<TopupEntity> result = topupService.getTopupHistory("user-settled");

            assertThat(result).hasSize(2);
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Case 53: Should sync pending topups with PayOS")
        void should_SyncPendingTopups_when_PendingTopupsExist() {
            TopupEntity pending = TopupEntity.builder().orderCode(503L).amount(BigDecimal.valueOf(50000)).status(TopupStatus.PENDING).userId("user-sync").build();
            when(topupRepository.findByUserIdOrderByCreatedAtDesc("user-sync")).thenReturn(List.of(pending));

            Map<String, Object> payosData = Map.of("status", "CANCELLED");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/503"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            topupService.getTopupHistory("user-sync");

            verify(topupRepository).updateStatusByOrderCode(503L, TopupStatus.PENDING, TopupStatus.CANCELLED);
        }

        @Test
        @DisplayName("Case 54: Should re-fetch history from DB when any sync changed status")
        void should_RefetchHistory_when_AnySyncChangedStatus() {
            TopupEntity pending = TopupEntity.builder().orderCode(504L).amount(BigDecimal.valueOf(50000)).status(TopupStatus.PENDING).userId("user-refetch").build();
            TopupEntity updated = TopupEntity.builder().orderCode(504L).amount(BigDecimal.valueOf(50000)).status(TopupStatus.CANCELLED).userId("user-refetch").build();

            when(topupRepository.findByUserIdOrderByCreatedAtDesc("user-refetch"))
                    .thenReturn(List.of(pending))
                    .thenReturn(List.of(updated));

            Map<String, Object> payosData = Map.of("status", "CANCELLED");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/504"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            List<TopupEntity> result = topupService.getTopupHistory("user-refetch");

            verify(topupRepository, times(2)).findByUserIdOrderByCreatedAtDesc("user-refetch");
            assertThat(result.get(0).getStatus()).isEqualTo(TopupStatus.CANCELLED);
        }

        @Test
        @DisplayName("Case 55: Should return original history when sync result is still PENDING")
        void should_ReturnOriginalHistory_when_AllSyncsStillPending() {
            TopupEntity pending = TopupEntity.builder().orderCode(505L).amount(BigDecimal.valueOf(50000)).status(TopupStatus.PENDING).userId("user-still-pending").build();
            when(topupRepository.findByUserIdOrderByCreatedAtDesc("user-still-pending")).thenReturn(List.of(pending));

            Map<String, Object> payosData = Map.of("status", "PENDING");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/505"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            List<TopupEntity> result = topupService.getTopupHistory("user-still-pending");

            verify(topupRepository, times(1)).findByUserIdOrderByCreatedAtDesc("user-still-pending");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Case 56: Should skip failed sync for one topup and continue with others")
        void should_SkipFailedSync_when_PayosApiFailsForOneTopup() {
            TopupEntity t1 = TopupEntity.builder().orderCode(506L).amount(BigDecimal.valueOf(50000)).status(TopupStatus.PENDING).userId("user-skip-err").build();
            TopupEntity t2 = TopupEntity.builder().orderCode(507L).amount(BigDecimal.valueOf(50000)).status(TopupStatus.PENDING).userId("user-skip-err").build();
            when(topupRepository.findByUserIdOrderByCreatedAtDesc("user-skip-err")).thenReturn(List.of(t1, t2));

            // t1 fails
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/506"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("API error"));
            // t2 succeeds
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/507"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", Map.of("status", "EXPIRED")), HttpStatus.OK));

            topupService.getTopupHistory("user-skip-err");

            verify(topupRepository).updateStatusByOrderCode(507L, TopupStatus.PENDING, TopupStatus.EXPIRED);
        }
    }

    @Nested
    @DisplayName("2.7 Cancel Topup (cancelTopup)")
    class CancelTopupTests {

        @Test
        @DisplayName("Case 57: Should throw NotFoundException when orderCode is not found for user")
        void should_ThrowNotFound_when_OrderCodeNotFound() {
            when(topupRepository.findByOrderCodeAndUserId(601L, "user-not-found")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> topupService.cancelTopup(601L, "user-not-found"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Không tìm thấy giao dịch nạp tiền");
        }

        @Test
        @DisplayName("Case 58: Should throw BadRequestException when topup is not in PENDING status")
        void should_ThrowBadRequest_when_TopupIsNotPending() {
            TopupEntity topup = TopupEntity.builder().orderCode(602L).userId("user-paid").status(TopupStatus.PAID).build();
            when(topupRepository.findByOrderCodeAndUserId(602L, "user-paid")).thenReturn(Optional.of(topup));

            assertThatThrownBy(() -> topupService.cancelTopup(602L, "user-paid"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Chỉ có thể huỷ giao dịch đang chờ thanh toán");
        }

        @Test
        @DisplayName("Case 59: Should cancel on PayOS and update DB to CANCELLED when topup is PENDING")
        void should_CancelOnPayosAndUpdateDB_when_TopupIsPending() {
            Long orderCode = 603L;
            TopupEntity topup = TopupEntity.builder().orderCode(orderCode).userId("user-cancel").status(TopupStatus.PENDING).build();
            when(topupRepository.findByOrderCodeAndUserId(orderCode, "user-cancel")).thenReturn(Optional.of(topup));

            when(restTemplate.postForEntity(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode + "/cancel"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("code", "00"), HttpStatus.OK));
            when(topupRepository.updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.CANCELLED)).thenReturn(1);

            topupService.cancelTopup(orderCode, "user-cancel");

            verify(topupRepository).updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.CANCELLED);
        }

        @Test
        @DisplayName("Case 60: Should throw BadRequestException when updateStatusByOrderCode returns 0 (race condition)")
        void should_ThrowBadRequest_when_TopupAlreadyProcessedDuringCancel() {
            Long orderCode = 604L;
            TopupEntity topup = TopupEntity.builder().orderCode(orderCode).userId("user-race").status(TopupStatus.PENDING).build();
            when(topupRepository.findByOrderCodeAndUserId(orderCode, "user-race")).thenReturn(Optional.of(topup));

            when(restTemplate.postForEntity(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode + "/cancel"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("code", "00"), HttpStatus.OK));
            when(topupRepository.updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.CANCELLED)).thenReturn(0);

            assertThatThrownBy(() -> topupService.cancelTopup(orderCode, "user-race"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Giao dịch đã được xử lý, không thể huỷ");
        }

        @Test
        @DisplayName("Case 61: Fallback - Accept cancel when PayOS cancel API fails but fallback sync says CANCELLED")
        void should_AcceptCancel_when_PayosFailsButStatusIsCancelled() {
            Long orderCode = 605L;
            TopupEntity topup = TopupEntity.builder().orderCode(orderCode).userId("user-fallback-cancel").status(TopupStatus.PENDING).build();
            when(topupRepository.findByOrderCodeAndUserId(orderCode, "user-fallback-cancel")).thenReturn(Optional.of(topup));

            // PayOS cancel fails
            when(restTemplate.postForEntity(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode + "/cancel"), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("Cancel endpoint failed"));

            // Fallback sync says CANCELLED
            Map<String, Object> payosData = Map.of("status", "CANCELLED");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            topupService.cancelTopup(orderCode, "user-fallback-cancel");

            // Does not throw
            verify(topupRepository).updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.CANCELLED);
        }

        @Test
        @DisplayName("Case 62: Fallback - Accept cancel when PayOS cancel API fails but fallback sync says EXPIRED")
        void should_AcceptCancel_when_PayosFailsButStatusIsExpired() {
            Long orderCode = 606L;
            TopupEntity topup = TopupEntity.builder().orderCode(orderCode).userId("user-fallback-exp").status(TopupStatus.PENDING).build();
            when(topupRepository.findByOrderCodeAndUserId(orderCode, "user-fallback-exp")).thenReturn(Optional.of(topup));

            when(restTemplate.postForEntity(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode + "/cancel"), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("Cancel endpoint failed"));

            Map<String, Object> payosData = Map.of("status", "EXPIRED");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            topupService.cancelTopup(orderCode, "user-fallback-exp");

            verify(topupRepository).updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.EXPIRED);
        }

        @Test
        @DisplayName("Case 63: Fallback - Throw BadRequestException when PayOS cancel fails and fallback sync says PAID")
        void should_ThrowBadRequest_when_PayosFailsAndStatusIsPaid() {
            Long orderCode = 607L;
            TopupEntity topup = TopupEntity.builder().orderCode(orderCode).amount(BigDecimal.valueOf(50000)).userId("user-fallback-paid").status(TopupStatus.PENDING).build();
            when(topupRepository.findByOrderCodeAndUserId(orderCode, "user-fallback-paid")).thenReturn(Optional.of(topup));

            when(restTemplate.postForEntity(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode + "/cancel"), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("Cancel endpoint failed"));

            Map<String, Object> payosData = Map.of("status", "PAID", "amount", 50000);
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            assertThatThrownBy(() -> topupService.cancelTopup(orderCode, "user-fallback-paid"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Giao dịch đã thanh toán thành công, không thể huỷ");
        }

        @Test
        @DisplayName("Case 64: Fallback - Throw BadRequestException when PayOS cancel fails and status is still PENDING")
        void should_ThrowBadRequest_when_PayosFailsAndStatusIsStillPending() {
            Long orderCode = 608L;
            TopupEntity topup = TopupEntity.builder().orderCode(orderCode).userId("user-fallback-pending").status(TopupStatus.PENDING).build();
            when(topupRepository.findByOrderCodeAndUserId(orderCode, "user-fallback-pending")).thenReturn(Optional.of(topup));

            when(restTemplate.postForEntity(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode + "/cancel"), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("Cancel endpoint failed"));

            Map<String, Object> payosData = Map.of("status", "PENDING");
            when(restTemplate.exchange(eq(PAYOS_URL + "/v2/payment-requests/" + orderCode), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", payosData), HttpStatus.OK));

            assertThatThrownBy(() -> topupService.cancelTopup(orderCode, "user-fallback-pending"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Không thể huỷ giao dịch trên payOS");
        }
    }

    @Nested
    @DisplayName("2.8 Business Invariants — Topup")
    class TopupBusinessInvariantsTests {

        @Test
        @DisplayName("Invariant 65: Topup PAID -> wallet must increase exactly once by the exact topup amount")
        void invariant_WalletIncreasedExactlyOnce_when_TopupPaid() {
            Long orderCode = 701L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-inv-paid")
                    .amount(BigDecimal.valueOf(200000))
                    .status(TopupStatus.PENDING)
                    .build();

            when(topupRepository.markAsPaid(eq(orderCode), eq(TopupStatus.PENDING), eq(TopupStatus.PAID), any(), any(), any(), any(), any()))
                    .thenReturn(1)  // First attempt: success
                    .thenReturn(0); // Retry attempt: skipped
            when(walletRepository.creditBalance("user-inv-paid", BigDecimal.valueOf(200000))).thenReturn(1);
            when(walletRepository.findById("user-inv-paid")).thenReturn(Optional.of(WalletEntity.builder().userId("user-inv-paid").balance(BigDecimal.valueOf(400000)).build()));

            topupService.creditWallet(topup, null);
            topupService.creditWallet(topup, null); // Second attempt

            verify(walletRepository, times(1)).creditBalance("user-inv-paid", BigDecimal.valueOf(200000));
        }

        @Test
        @DisplayName("Invariant 66: Topup PAID -> cannot be CANCELLED")
        void invariant_CannotCancelPaidTopup() {
            Long orderCode = 702L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-inv-no-cancel")
                    .amount(BigDecimal.valueOf(100000))
                    .status(TopupStatus.PAID)
                    .build();
            when(topupRepository.findByOrderCodeAndUserId(orderCode, "user-inv-no-cancel")).thenReturn(Optional.of(topup));

            assertThatThrownBy(() -> topupService.cancelTopup(orderCode, "user-inv-no-cancel"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Chỉ có thể huỷ giao dịch đang chờ thanh toán");

            verify(walletRepository, never()).debitBalance(anyString(), any());
            verify(topupRepository, never()).updateStatusByOrderCode(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Invariant 67: Topup CANCELLED -> cannot be CREDITED to wallet")
        void invariant_CannotCreditCancelledTopup() {
            Long orderCode = 703L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .status(TopupStatus.CANCELLED)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            String status = topupService.verifyTopup(orderCode);

            assertThat(status).isEqualTo("CANCELLED");
            verify(walletRepository, never()).creditBalance(anyString(), any());
            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Invariant 68: Topup EXPIRED -> cannot be CREDITED to wallet")
        void invariant_CannotCreditExpiredTopup() {
            Long orderCode = 704L;
            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .status(TopupStatus.EXPIRED)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            String status = topupService.verifyTopup(orderCode);

            assertThat(status).isEqualTo("EXPIRED");
            verify(walletRepository, never()).creditBalance(anyString(), any());
            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Invariant 69: Webhook amount mismatch -> wallet balance must NOT change")
        void invariant_WalletUnchanged_when_AmountMismatch() {
            Long orderCode = 705L;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", 20000);
            data.put("code", "00");
            data.put("orderCode", orderCode);
            data.put("status", "PAID");

            String dataStr = "amount=20000&code=00&orderCode=" + orderCode + "&status=PAID";
            String signature = computeHmacSha256(dataStr, CHECKSUM_KEY);
            Map<String, Object> webhook = Map.of("data", data, "signature", signature);

            TopupEntity topup = TopupEntity.builder()
                    .orderCode(orderCode)
                    .userId("user-inv-mismatch")
                    .amount(BigDecimal.valueOf(50000)) // Expects 50k, webhook gave 20k
                    .status(TopupStatus.PENDING)
                    .build();
            when(topupRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(topup));

            topupService.handleWebhook(webhook);

            verify(walletRepository, never()).creditBalance(anyString(), any());
            verify(walletRepository, never()).save(any());
            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Invariant 70: Webhook invalid signature -> wallet balance must NOT change")
        void invariant_WalletUnchanged_when_SignatureInvalid() {
            Long orderCode = 706L;
            Map<String, Object> data = Map.of("amount", 50000, "code", "00", "orderCode", orderCode, "status", "PAID");
            Map<String, Object> webhook = Map.of("data", data, "signature", "forged-signature-xyz");

            topupService.handleWebhook(webhook);

            verifyNoInteractions(walletRepository);
            verify(topupRepository, never()).markAsPaid(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
