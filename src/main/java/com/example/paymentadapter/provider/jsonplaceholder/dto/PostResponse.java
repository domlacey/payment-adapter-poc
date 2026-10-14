package com.example.paymentadapter.provider.jsonplaceholder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Provider wire format returned by {@code GET /posts/{id}} and {@code POST /posts}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostResponse(Integer id, Integer userId, String title, String body) {
}
