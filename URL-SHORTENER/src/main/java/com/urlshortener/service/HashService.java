package com.urlshortener.service;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class HashService {

    private static final int SHORT_CODE_LENGTH = 7;

    public String generateShortCode(String longUrl) {
        return sha256Hash(longUrl);
    }

    public String generateShortCodeWithSalt(String longUrl, int iteration) {
        return sha256Hash(longUrl + iteration);
    }

    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.substring(0, SHORT_CODE_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

}
