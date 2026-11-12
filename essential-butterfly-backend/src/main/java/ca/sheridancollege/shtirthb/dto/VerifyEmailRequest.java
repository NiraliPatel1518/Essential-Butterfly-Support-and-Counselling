package ca.sheridancollege.shtirthb.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyEmailRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "Please enter the 6-digit verification code.")
    private String code;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
