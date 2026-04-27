package ru.normacontrol.domain.event;

/**
 * Domain port for publishing application events.
 */
public interface DomainEventPublisher {

    /**
     * Publish an event to subscribed listeners.
     *
     * @param event event instance
     * @throws IllegalArgumentException when event is {@code null}
     */
    void publish(Object event);
}
