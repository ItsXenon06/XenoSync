package com.xenosync.exception;
import java.time.OffsetDateTime;
public record ErrorResponse(String error, String message, OffsetDateTime timestamp) {}