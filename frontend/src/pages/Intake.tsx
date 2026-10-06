import { useState } from "react";
import "./intake.css";

function Intake() {
  const [fullName, setFullName] = useState("");
  const [guardianName, setGuardianName] = useState("");
  const [contactInfo, setContactInfo] = useState("");
  const [preferredContact, setPreferredContact] = useState("");
  const [overview, setOverview] = useState("");
  const [supportNeeds, setSupportNeeds] = useState("");
  const [respiteGoals, setRespiteGoals] = useState("");
  const [subjectFocus, setSubjectFocus] = useState("");
  const [additionalNotes, setAdditionalNotes] = useState("");

  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    setMessage("");
    setError("");
    setIsSubmitting(true);

    const token = localStorage.getItem("authToken");

    if (!token) {
      setError("Please log in before submitting the intake form.");
      setIsSubmitting(false);
      return;
    }

    try {
      const response = await fetch("http://localhost:8080/api/intake", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          clientFullName: fullName,
          parentGuardianName: guardianName,
          contactInfo: contactInfo,
          preferredContactMethod: preferredContact,
          overview: overview,
          supportNeeds: supportNeeds,
          respiteGoals: respiteGoals,
          subjectFocus: subjectFocus,
          additionalNotes: additionalNotes,
        }),
      });

      const result = await response.text();

      if (!response.ok) {
        setError(result || "Unable to submit the intake form.");
        return;
      }

      setMessage("Your intake information has been submitted successfully.");

      // Clear the form after successful submission
      setFullName("");
      setGuardianName("");
      setContactInfo("");
      setPreferredContact("");
      setOverview("");
      setSupportNeeds("");
      setRespiteGoals("");
      setSubjectFocus("");
      setAdditionalNotes("");

    } catch (err) {
      setError(
        "Unable to connect to the server. Please make sure the backend is running."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="intake-page">
      <section className="intake-container">

        <div className="intake-intro">
          <p className="intake-eyebrow">Secure Intake Handoff</p>

          <h1>Let’s explore what support could look like.</h1>

          <p>
            A thoughtful first step toward understanding your needs,
            goals, and the support that may be helpful.
          </p>

          <div className="intake-note">
            <h2>A thoughtful first step</h2>
            <p>
              Share a little about yourself and what you are looking for.
              This information helps us better understand how we may be
              able to support you.
            </p>
          </div>
        </div>

        <div className="intake-form-card">
          <form onSubmit={handleSubmit}>

            <div className="form-group">
              <label htmlFor="fullName">
                Client's Full Name <span>*</span>
              </label>

              <input
                id="fullName"
                type="text"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="guardianName">
                Parent/Guardian Name
              </label>

              <input
                id="guardianName"
                type="text"
                value={guardianName}
                onChange={(e) => setGuardianName(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label htmlFor="contactInfo">
                Contact Information <span>*</span>
              </label>

              <input
                id="contactInfo"
                type="text"
                value={contactInfo}
                onChange={(e) => setContactInfo(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="preferredContact">
                Preferred Contact Method <span>*</span>
              </label>

              <select
                id="preferredContact"
                value={preferredContact}
                onChange={(e) => setPreferredContact(e.target.value)}
                required
              >
                <option value="">Select an option</option>
                <option value="email">Email</option>
                <option value="phone">Phone</option>
                <option value="text">Text</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="overview">
                Overview <span>*</span>
              </label>

              <textarea
                id="overview"
                value={overview}
                onChange={(e) => setOverview(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="supportNeeds">
                Support Needs <span>*</span>
              </label>

              <textarea
                id="supportNeeds"
                value={supportNeeds}
                onChange={(e) => setSupportNeeds(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="respiteGoals">
                Goals for Respite
              </label>

              <textarea
                id="respiteGoals"
                value={respiteGoals}
                onChange={(e) => setRespiteGoals(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label htmlFor="subjectFocus">
                Subject Focus
              </label>

              <input
                id="subjectFocus"
                type="text"
                value={subjectFocus}
                onChange={(e) => setSubjectFocus(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label htmlFor="additionalNotes">
                Additional Notes
              </label>

              <textarea
                id="additionalNotes"
                value={additionalNotes}
                onChange={(e) => setAdditionalNotes(e.target.value)}
              />
            </div>

            {error && (
              <p className="error-message">
                {error}
              </p>
            )}

            {message && (
              <p className="success-message">
                {message}
              </p>
            )}

            <button type="submit" disabled={isSubmitting}>
              {isSubmitting
                ? "Submitting..."
                : "Continue to Secure Intake"}
            </button>

            <p className="privacy-note">
              Your information is handled securely and is only used
              to understand your support needs.
            </p>

          </form>
        </div>

      </section>
    </main>
  );
}

export default Intake;