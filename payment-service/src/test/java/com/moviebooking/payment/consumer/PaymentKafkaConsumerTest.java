package com.moviebooking.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.KafkaConstants;
import com.moviebooking.common.event.EventPayloads.SeatsReservedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.idempotency.IdempotencyService;
import com.moviebooking.payment.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentKafkaConsumerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private IdempotencyService idempotencyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentKafkaConsumer paymentKafkaConsumer;

    @BeforeEach
    void setUp() {
        paymentKafkaConsumer = new PaymentKafkaConsumer(paymentService, idempotencyService, objectMapper);
    }

    private ConsumerRecord<String, String> createRecord(String eventType, String eventId, String value, boolean useHeaders) {
        RecordHeaders headers = new RecordHeaders();
        if (useHeaders) {
            if (eventType != null) {
                headers.add(new RecordHeader(KafkaConstants.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
            }
            if (eventId != null) {
                headers.add(new RecordHeader(KafkaConstants.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)));
            }
        }
        ConsumerRecord<String, String> record = new ConsumerRecord<>("seat.events", 0, 0L, "key", value);
        headers.forEach(h -> record.headers().add(h));
        return record;
    }

    @Nested
    @DisplayName("3.1 Parsing, Routing & Filtering")
    class ParsingAndRoutingTests {

        @Test
        @DisplayName("Case 1: Should process SEATS_RESERVED when event type is in headers")
        void should_ProcessSeatsReserved_when_EventTypeInHeader() throws Exception {
            String eventId = "evt-hdr-1";
            SeatsReservedPayload payload = SeatsReservedPayload.builder()
                    .bookingId("b-100")
                    .userId("u-100")
                    .showtimeId("st-100")
                    .seatIds(List.of("A1", "A2"))
                    .totalAmount(160000.0)
                    .build();
            String json = objectMapper.writeValueAsString(Map.of("payload", payload));
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json, true);

            doAnswer(inv -> {
                Runnable handler = inv.getArgument(2);
                handler.run();
                return true;
            }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));

            paymentKafkaConsumer.handleSeatEvents(record);

            verify(paymentService).processPayment(argThat(p -> "b-100".equals(p.getBookingId()) && "u-100".equals(p.getUserId())));
        }

        @Test
        @DisplayName("Case 2: Should process SEATS_RESERVED when event type is in JSON body fallback")
        void should_ProcessSeatsReserved_when_EventTypeInBody() throws Exception {
            String eventId = "evt-body-2";
            SeatsReservedPayload payload = SeatsReservedPayload.builder()
                    .bookingId("b-200")
                    .userId("u-200")
                    .showtimeId("st-200")
                    .seatIds(List.of("B1"))
                    .totalAmount(80000.0)
                    .build();
            String json = objectMapper.writeValueAsString(Map.of(
                    KafkaConstants.HEADER_EVENT_TYPE, Events.SEATS_RESERVED,
                    KafkaConstants.HEADER_EVENT_ID, eventId,
                    "payload", payload
            ));
            ConsumerRecord<String, String> record = createRecord(null, null, json, false);

            doAnswer(inv -> {
                Runnable handler = inv.getArgument(2);
                handler.run();
                return true;
            }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));

            paymentKafkaConsumer.handleSeatEvents(record);

            verify(paymentService).processPayment(argThat(p -> "b-200".equals(p.getBookingId())));
        }

        @Test
        @DisplayName("Case 3: Should extract payload from nested payload field when payload key exists")
        void should_ExtractPayloadFromNestedField_when_PayloadKeyExists() throws Exception {
            String eventId = "evt-nested-3";
            SeatsReservedPayload payload = SeatsReservedPayload.builder()
                    .bookingId("b-300")
                    .userId("u-300")
                    .seatIds(List.of("C1"))
                    .totalAmount(90000.0)
                    .build();
            String json = objectMapper.writeValueAsString(Map.of("payload", payload, "extraInfo", "ignore-me"));
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json, true);

            doAnswer(inv -> {
                Runnable handler = inv.getArgument(2);
                handler.run();
                return true;
            }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));

            paymentKafkaConsumer.handleSeatEvents(record);

            verify(paymentService).processPayment(argThat(p -> "b-300".equals(p.getBookingId())));
        }

        @Test
        @DisplayName("Case 4: Should extract payload from root map when no nested payload key")
        void should_ExtractPayloadFromRoot_when_NoPayloadKey() throws Exception {
            String eventId = "evt-root-4";
            Map<String, Object> rootPayload = Map.of(
                    "bookingId", "b-400",
                    "userId", "u-400",
                    "showtimeId", "st-400",
                    "seatIds", List.of("D1"),
                    "totalAmount", 100000.0
            );
            String json = objectMapper.writeValueAsString(rootPayload);
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json, true);

            doAnswer(inv -> {
                Runnable handler = inv.getArgument(2);
                handler.run();
                return true;
            }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));

            paymentKafkaConsumer.handleSeatEvents(record);

            verify(paymentService).processPayment(argThat(p -> "b-400".equals(p.getBookingId())));
        }

        @Test
        @DisplayName("Case 5: Should skip processing when eventType is not SEATS_RESERVED")
        void should_SkipProcessing_when_EventTypeIsNotSeatsReserved() {
            String json = "{\"payload\":{\"bookingId\":\"b-500\"}}";
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_CONFIRMED, "evt-500", json, true);

            paymentKafkaConsumer.handleSeatEvents(record);

            verifyNoInteractions(idempotencyService);
            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Case 6: Should skip processing when record value is empty or null")
        void should_SkipProcessing_when_RecordValueIsEmpty() {
            ConsumerRecord<String, String> emptyRecord = new ConsumerRecord<>("seat.events", 0, 0L, "key", "");
            paymentKafkaConsumer.handleSeatEvents(emptyRecord);

            ConsumerRecord<String, String> nullRecord = new ConsumerRecord<>("seat.events", 0, 0L, "key", null);
            paymentKafkaConsumer.handleSeatEvents(nullRecord);

            verifyNoInteractions(idempotencyService);
            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Case 7: Should skip processing when eventType or eventId is missing")
        void should_SkipProcessing_when_EventTypeOrEventIdMissing() {
            String json = "{\"bookingId\":\"b-700\"}";
            ConsumerRecord<String, String> record = createRecord(null, null, json, false);

            paymentKafkaConsumer.handleSeatEvents(record);

            verifyNoInteractions(idempotencyService);
            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("3.2 Idempotency & Error Handling")
    class IdempotencyAndErrorHandlingTests {

        @Test
        @DisplayName("Case 8: Should wrap event processing in IdempotencyService")
        void should_WrapProcessingInIdempotencyGuard() {
            String eventId = "evt-guard-8";
            String json = "{\"payload\":{\"bookingId\":\"b-800\",\"userId\":\"u-800\",\"totalAmount\":100000.0}}";
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json, true);

            paymentKafkaConsumer.handleSeatEvents(record);

            verify(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));
        }

        @Test
        @DisplayName("Case 9: Should not crash when record contains invalid JSON")
        void should_NotCrash_when_RecordContainsInvalidJson() {
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, "evt-broken-9", "not-a-valid-json", true);

            paymentKafkaConsumer.handleSeatEvents(record);

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("Case 10: Should catch exception and not rethrow when payload deserialization fails")
        void should_NotCrash_when_PayloadDeserializationFails() {
            String eventId = "evt-bad-payload-10";
            String json = "{\"payload\":\"invalid-payload-structure\"}";
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json, true);

            doAnswer(inv -> {
                Runnable handler = inv.getArgument(2);
                handler.run();
                return true;
            }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));

            paymentKafkaConsumer.handleSeatEvents(record);

            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("3.3 Duplicate Message Handling")
    class DuplicateMessageHandlingTests {

        @Test
        @DisplayName("Case 11: Duplicate Kafka message with same eventId should process payment exactly ONCE")
        void should_ProcessOnlyOnce_when_DuplicateKafkaMessageReceived() throws Exception {
            String eventId = "evt-dup-11";
            SeatsReservedPayload payload = SeatsReservedPayload.builder()
                    .bookingId("b-dup-11")
                    .userId("u-dup-11")
                    .totalAmount(150000.0)
                    .build();
            String json = objectMapper.writeValueAsString(Map.of("payload", payload));
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json, true);

            // First delivery: idempotency claims successfully and executes handler
            // Second delivery (duplicate): idempotency returns false and skips handler
            when(idempotencyService.processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class)))
                    .thenAnswer(inv -> {
                        Runnable handler = inv.getArgument(2);
                        handler.run();
                        return true;
                    })
                    .thenReturn(false);

            // First delivery
            paymentKafkaConsumer.handleSeatEvents(record);
            // Second delivery (duplicate from Kafka broker)
            paymentKafkaConsumer.handleSeatEvents(record);

            // processPayment must be called EXACTLY ONCE
            verify(paymentService, times(1)).processPayment(any(SeatsReservedPayload.class));
            verify(idempotencyService, times(2)).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));
        }

        @Test
        @DisplayName("Case 12: Consumer must pass correct eventId to idempotency guard")
        void should_PassCorrectEventIdToIdempotencyGuard() {
            String eventId = "unique-event-id-xyz-123";
            String json = "{\"payload\":{\"bookingId\":\"b-12\"}}";
            ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json, true);

            paymentKafkaConsumer.handleSeatEvents(record);

            verify(idempotencyService).processWithIdempotency(eq("unique-event-id-xyz-123"), eq(Events.SEATS_RESERVED), any(Runnable.class));
        }
    }
}
