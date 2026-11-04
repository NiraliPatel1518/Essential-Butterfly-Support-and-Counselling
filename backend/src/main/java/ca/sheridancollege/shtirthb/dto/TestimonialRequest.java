package ca.sheridancollege.shtirthb.dto;

import java.util.List;

public class TestimonialRequest {
    private String displayName;
    private String content;
    private Boolean publicationConsent;
    private List<String> photos;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { displayName = v; }
    public String getContent() { return content; }
    public void setContent(String v) { content = v; }
    public Boolean getPublicationConsent() { return publicationConsent; }
    public void setPublicationConsent(Boolean v) { publicationConsent = v; }
    public List<String> getPhotos() { return photos; }
    public void setPhotos(List<String> v) { photos = v; }
}
