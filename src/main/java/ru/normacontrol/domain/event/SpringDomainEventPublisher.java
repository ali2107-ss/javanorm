package ru.normacontrol.domain.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring-based implementation of the domain event publisher.
 */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publish an event through Spring's event bus.
     *
     * @param event event instance
     * @throws IllegalArgumentException when event is {@code null}
     */
    @Override
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("Event must not be null");
        }
        applicationEventPublisher.publishEvent(event);
    }
}
