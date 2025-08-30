package com.oolshik.backend.web.dto;

public class PhoneRevealDtos {
    public record RevealPhoneResponse(String phoneNumber, long revealCount) {}
}