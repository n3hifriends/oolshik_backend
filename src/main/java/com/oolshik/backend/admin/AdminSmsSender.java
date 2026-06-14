package com.oolshik.backend.admin;

public interface AdminSmsSender {
    void send(String phoneE164, String text);
}
