package com.moviebooking.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventHandlerRegistryTest {

    @Test
    @DisplayName("Should correctly register and retrieve handlers by eventType")
    void shouldRegisterAndRetrieveHandlers() {
        EventHandler<String> mockHandler = mock(EventHandler.class);
        when(mockHandler.supportedEventType()).thenReturn("TEST_EVENT");

        EventHandlerRegistry registry = EventHandlerRegistry.of(List.of(mockHandler));

        assertTrue(registry.supports("TEST_EVENT"));
        assertFalse(registry.supports("UNKNOWN_EVENT"));

        Optional<EventHandler<String>> retrieved = registry.getHandler("TEST_EVENT");
        assertTrue(retrieved.isPresent());
        assertEquals(mockHandler, retrieved.get());
    }

    @Test
    @DisplayName("Should gracefully handle empty handler list")
    void shouldHandleEmptyList() {
        EventHandlerRegistry registry = new EventHandlerRegistry(Optional.empty());

        assertFalse(registry.supports("ANY_EVENT"));
        assertTrue(registry.getHandler("ANY_EVENT").isEmpty());
        assertTrue(registry.getSupportedEventTypes().isEmpty());
    }

    @Test
    @DisplayName("Should gracefully handle null in convenience factory")
    void shouldHandleNullList() {
        EventHandlerRegistry registry = EventHandlerRegistry.of(null);

        assertFalse(registry.supports("ANY_EVENT"));
        assertTrue(registry.getHandler("ANY_EVENT").isEmpty());
    }
}
