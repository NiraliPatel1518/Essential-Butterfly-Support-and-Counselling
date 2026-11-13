package ca.sheridancollege.shtirthb.dto;

// A picture ready to be sent to the browser.
public record MemoryWallImage(String contentType, byte[] content) {}
