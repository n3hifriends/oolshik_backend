package com.oolshik.backend.web.error;

import com.oolshik.backend.web.dto.ActiveRequestDtos.ActiveRequestCapReachedResponse;

import java.util.List;
import java.util.UUID;

public class ActiveRequestCapReachedException extends RuntimeException {

    public static final String CODE = "ACTIVE_REQUEST_CAP_REACHED";
    private static final String MESSAGE_KEY = "errors.activeRequestCapReached";

    private final ActiveRequestCapReachedResponse response;

    public ActiveRequestCapReachedException(
            int cap,
            int activeCount,
            List<UUID> activeRequestIds,
            UUID suggestedRequestId
    ) {
        super(MESSAGE_KEY);
        this.response = new ActiveRequestCapReachedResponse(
                CODE,
                MESSAGE_KEY,
                cap,
                activeCount,
                List.copyOf(activeRequestIds),
                suggestedRequestId
        );
    }

    public ActiveRequestCapReachedResponse response() {
        return response;
    }
}
