
package ca.sheridancollege.shtirthb.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminLoginResendRequest {

    @NotBlank
    private String challengeToken;

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }
}
