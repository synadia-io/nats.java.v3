package io.synadia.client.utils;

import io.synadia.client.ConnectionEvents;
import io.synadia.client.ErrorListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// ----------------------------------------------------------------------------------------------------
// Prep
// ----------------------------------------------------------------------------------------------------
public class ListenerFuture extends CompletableFuture<Void> {
    public ConnectionEvents eventType;
    public String error;
    public Class<?> exceptionClass;
    public String exContains;
    public ListenerStatusType lbfStatusType;
    public int statusCode = -1;
    public String fcSubject;
    public ErrorListener.FlowControlSource fcSource;
    public boolean forHeartbeat;
    public boolean forSocketWriteTimeout;

    public Throwable receivedException;

    public int validateTimeout;

    ListenerFuture(ConnectionEvents type, int validateTimeout) {
        this.eventType = type;
        this.validateTimeout = validateTimeout;
    }

    ListenerFuture(Class<?> exceptionClass, int validateTimeout) {
        this.exceptionClass = exceptionClass;
        this.validateTimeout = validateTimeout;
    }

    ListenerFuture(Class<?> exceptionClass, String contains, int validateTimeout) {
        this.exceptionClass = exceptionClass;
        this.exContains = contains;
        this.validateTimeout = validateTimeout;
    }

    ListenerFuture(String errorText, int validateTimeout) {
        error = errorText;
        this.validateTimeout = validateTimeout;
    }

    ListenerFuture(ListenerStatusType type, int statusCode, int validateTimeout) {
        lbfStatusType = type;
        this.statusCode = statusCode;
        this.validateTimeout = validateTimeout;
    }

    ListenerFuture(String fcSubject, ErrorListener.FlowControlSource fcSource, int validateTimeout) {
        this.fcSubject = fcSubject;
        this.fcSource = fcSource;
        this.validateTimeout = validateTimeout;
    }

    public ListenerFuture(boolean forHeartbeat, boolean forSocketWriteTimeout, int validateTimeout) {
        this.forHeartbeat = forHeartbeat;
        this.forSocketWriteTimeout = forSocketWriteTimeout;
        this.validateTimeout = validateTimeout;
    }

    @Override
    public String toString() {
        return "ListenerFuture{" + getDetails() + "}";
    }

    public List<String> getDetails() {
        List<String> details = new ArrayList<>();
        if (eventType != null) {
            details.add(eventType.toString());
        }
        if (error != null) {
            details.add(error);
        }
        if (exceptionClass != null) {
            details.add(exceptionClass.toString());
        }
        if (lbfStatusType != null) {
            details.add(lbfStatusType.toString());
        }
        if (statusCode != -1) {
            details.add(Integer.toString(statusCode));
        }
        if (fcSubject != null) {
            details.add(fcSubject);
        }
        if (fcSource != null) {
            details.add(fcSource.toString());
        }
        return details;
    }
}
