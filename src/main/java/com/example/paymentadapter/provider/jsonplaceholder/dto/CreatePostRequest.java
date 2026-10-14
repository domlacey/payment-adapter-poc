package com.example.paymentadapter.provider.jsonplaceholder.dto;

/** Provider wire format for {@code POST /posts}. */
public record CreatePostRequest(String title, String body, Integer userId) {
}
