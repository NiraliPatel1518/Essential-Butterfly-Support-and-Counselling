package ca.sheridancollege.shtirthb.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class WebsiteUpdateRequest {

    @NotBlank
    @Size(max = 180)
    private String title;

    @NotBlank
    @Size(max = 20000)
    private String content;

    @NotNull
    private Boolean published;

    private List<String> photos = new ArrayList<>();
    private List<String> socialLinks = new ArrayList<>();

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Boolean getPublished() { return published; }
    public List<String> getPhotos() { return photos; }
    public List<String> getSocialLinks() { return socialLinks; }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setPublished(Boolean published) { this.published = published; }
    public void setPhotos(List<String> photos) { this.photos = photos; }
    public void setSocialLinks(List<String> socialLinks) {
        this.socialLinks = socialLinks;
    }
}