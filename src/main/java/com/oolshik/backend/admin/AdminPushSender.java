package com.oolshik.backend.admin;

import java.util.List;
import java.util.Map;

public interface AdminPushSender {

    record SendResult(boolean success, String error) {}

    String provider();

    Map<String, SendResult> sendBatch(List<String> tokens, String title, String body);
}
