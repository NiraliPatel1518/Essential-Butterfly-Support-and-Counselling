package ca.sheridancollege.shtirthb.dto;

public record ResourceDownload(
    String fileName,
    String contentType,
    byte[] content,
    boolean inline
) {}
