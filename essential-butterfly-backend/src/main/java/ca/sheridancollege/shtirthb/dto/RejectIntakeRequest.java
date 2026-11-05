
package ca.sheridancollege.shtirthb.dto;

import jakarta.validation.constraints.NotBlank;

public class RejectIntakeRequest {

    @NotBlank(message = "A rejection reason is required")
    private String rejectionReason;

    public RejectIntakeRequest() {
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
