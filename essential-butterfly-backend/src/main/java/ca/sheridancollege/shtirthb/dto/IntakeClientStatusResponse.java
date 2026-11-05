
package ca.sheridancollege.shtirthb.dto;

public class IntakeClientStatusResponse {

    private String status;
    private String rejectionReason;

    public IntakeClientStatusResponse(
            String status,
            String rejectionReason) {

        this.status = status;
        this.rejectionReason = rejectionReason;
    }

    public String getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
