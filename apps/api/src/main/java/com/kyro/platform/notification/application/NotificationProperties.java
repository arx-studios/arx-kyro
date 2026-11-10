package com.kyro.platform.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code kyro.notifications.*}. */
@ConfigurationProperties(prefix = "kyro.notifications")
public record NotificationProperties(Boolean emailEnabled) {

    public boolean enabled() {
        return emailEnabled == null || emailEnabled;
    }
}
