
package ca.sheridancollege.shtirthb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyPhoneRequest {

    @NotBlank(message = "Please enter the 6-digit verification code.")
    @Pattern(
        regexp = "^[0-9]{6}$",
        message = "Please enter the 6-digit verification code."
    )
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
