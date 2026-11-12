
package ca.sheridancollege.shtirthb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PhoneNumberRequest {

    @NotBlank(message = "Please enter your phone number.")
    @Pattern(
        regexp = "^\\+[1-9][0-9]{7,14}$",
        message = "Enter a valid phone number in international format, including the country code (for example, +14165551234)."
    )
    private String phoneNumber;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
