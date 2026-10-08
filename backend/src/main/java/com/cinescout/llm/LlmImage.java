package com.cinescout.llm;

/** A picture to show the model with a prompt: JPEG or PNG bytes and their media type. Never logged. */
public record LlmImage(byte[] bytes, String mediaType) {
}
