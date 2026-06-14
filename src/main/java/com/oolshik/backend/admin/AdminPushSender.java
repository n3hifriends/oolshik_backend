package com.oolshik.backend.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AdminPushSender {

    record SendResult(boolean success, String error) {}

    String provider();

    Map<String, SendResult> sendBatch(List<String> tokens, String title, String body, UUID broadcastId,
                                      String routeKey, String routeTargetId);
}
