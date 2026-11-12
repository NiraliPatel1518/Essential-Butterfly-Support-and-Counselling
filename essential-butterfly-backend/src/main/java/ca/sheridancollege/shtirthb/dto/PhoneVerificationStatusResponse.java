
package ca.sheridancollege.shtirthb.dto;

public record PhoneVerificationStatusResponse(
        boolean phoneVerified,
        String phoneNumber,
        String pendingPhoneNumber) {
}
