
package ca.sheridancollege.shtirthb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AdminLoginOtpRequest {

    @NotBlank
    private String challengeToken;

    @NotBlank
    @Pattern(
        regexp = "^[0-9]{6}$",
        message = "Enter the 6-digit verification code."
    )
    private String code;

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
