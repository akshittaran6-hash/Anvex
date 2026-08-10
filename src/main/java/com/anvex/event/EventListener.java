package com.anvex.event;

@FunctionalInterface
public interface EventListener {
    void onEvent(SecurityEvent event);
}