package com.moviebooking.common.event;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Context / Registry for Strategy Pattern.
 * Automatically collects all Spring beans implementing EventHandler via DI.
 *
 * Spring will inject all EventHandler beans as a List. If none exist,
 * the Optional wrapper ensures graceful degradation to an empty registry.
 */
@Component
public class EventHandlerRegistry {

    private final Map<String, EventHandler<?>> handlers;

    /**
     * Primary constructor used by Spring DI.
     * Also supports direct instantiation for testing via {@code new EventHandlerRegistry(Optional.of(list))}.
     */
    public EventHandlerRegistry(Optional<List<EventHandler<?>>> handlerList) {
        this.handlers = handlerList.orElse(Collections.emptyList()).stream()
                .collect(Collectors.toMap(
                        EventHandler::supportedEventType,
                        h -> h,
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Convenience constructor for testing — wraps the list in Optional internally.
     */
    public static EventHandlerRegistry of(List<EventHandler<?>> handlerList) {
        return new EventHandlerRegistry(Optional.ofNullable(handlerList));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<EventHandler<T>> getHandler(String eventType) {
        return Optional.ofNullable((EventHandler<T>) handlers.get(eventType));
    }

    public boolean supports(String eventType) {
        return handlers.containsKey(eventType);
    }

    public Set<String> getSupportedEventTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}
