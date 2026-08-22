package com.moviebooking.common.event;

/**
 * Strategy Pattern interface for handling asynchronous domain events.
 * Implementations encapsulate specific event processing strategies.
 *
 * @param <T> Payload type corresponding to the event
 */
public interface EventHandler<T> {

    /**
     * Identifies the event type this strategy handles (e.g. "SEATS_RESERVED").
     */
    String supportedEventType();

    /**
     * Target Java class used by Jackson to deserialize the incoming payload.
     */
    Class<T> payloadType();

    /**
     * Executes the domain business logic for this event strategy.
     *
     * @param payload Deserialized event payload
     */
    void handle(T payload);
}
