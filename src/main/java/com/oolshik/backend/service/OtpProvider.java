package com.oolshik.backend.service;

public interface OtpProvider {

    String providerId();

    void sendOtp(String phone, String message) throws OtpDeliveryException;
}
