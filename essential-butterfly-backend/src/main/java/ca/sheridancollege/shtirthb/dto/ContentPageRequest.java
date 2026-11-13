package ca.sheridancollege.shtirthb.dto;

/*
 * Body of PUT /api/admin/content/{pageKey}.
 *
 * Required fields are checked by ContentPageService so that one message can
 * name every missing field. publish=false only saves the draft.
 */
public class ContentPageRequest {

    private String title;
    private String body;
    private Boolean publish;

    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Boolean getPublish() { return publish; }

    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setPublish(Boolean publish) { this.publish = publish; }
}
