
package ca.sheridancollege.shtirthb.dto;

public record AdminLoginChallengeResponse(
        boolean requiresOtp,
        String challengeToken,
        String maskedEmail) {
}
