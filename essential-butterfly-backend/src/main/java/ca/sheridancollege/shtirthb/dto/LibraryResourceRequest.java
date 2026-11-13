package ca.sheridancollege.shtirthb.dto;

/*
 * The text fields of the Add / Edit Resource form. The file, if any, is sent
 * as a separate multipart part. publish=false saves the resource as a draft.
 *
 * linkUrls holds up to five web links, one per line. null means "leave the
 * links as they are"; an empty text means "no links". removeFile removes the
 * attached document or image.
 */
public record LibraryResourceRequest(
    String title,
    String description,
    String category,
    String linkUrls,
    String altText,
    boolean publish,
    boolean removeFile
) {}
