
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiUrl } from "../api/api";
import "./intake.css";

async function readIntakeError(response: Response): Promise<string> {
  const responseText = (await response.text()).trim();

  if (responseText) {
    try {
      const responseBody = JSON.parse(responseText) as {
        message?: string;
        error?: string;
      };

      if (responseBody.message?.trim()) return responseBody.message.trim();

      if (
        responseBody.error?.trim() &&
        responseBody.error.toLowerCase() !== "bad request"
      ) {
        return responseBody.error.trim();
      }
    } catch {
      if (!/^error\s*\d+$/i.test(responseText)) return responseText;
    }
  }

  if (response.status === 409) {
    return (
      "You already have an intake request under review. " +
      "Please wait until a decision has been made."
    );
  }

  if (response.status === 400) {
    return "Please review the form and correct any missing or invalid information.";
  }

  return "Unable to submit the intake form. Please try again.";
}

function Intake() {
  const navigate = useNavigate();

  const [fullName, setFullName] = useState("");
  const [guardianName, setGuardianName] = useState("");
  const [preferredContact, setPreferredContact] = useState("");

  // Details from the signed-in account. Email and phone cannot be edited
  // on this form; they are filled in from the account.
  const [accountName, setAccountName] = useState("");
  const [accountEmail, setAccountEmail] = useState("");
  const [accountPhone, setAccountPhone] = useState("");

  const contactInfo =
    preferredContact === "email"
      ? accountEmail
      : preferredContact === "phone" || preferredContact === "text"
        ? accountPhone
        : "";

  const [overview, setOverview] = useState("");
  const [supportNeeds, setSupportNeeds] = useState("");
  const [respiteGoals, setRespiteGoals] = useState("");
  const [subjectFocus, setSubjectFocus] = useState("");
  const [additionalNotes, setAdditionalNotes] = useState("");

  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem("authToken");

    if (!token) {
      return;
    }

    const controller = new AbortController();

    const loadAccount = async () => {
      try {
        const response = await fetch(apiUrl("/api/auth/me"), {
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal,
        });

        if (!response.ok) {
          return;
        }

        const account = (await response.json()) as {
          fullName?: string | null;
          email?: string | null;
          phoneNumber?: string | null;
        };

        setAccountName(account.fullName ?? "");
        setAccountEmail(account.email ?? "");
        setAccountPhone(account.phoneNumber ?? "");

        // The name is only a starting point and stays editable.
        setFullName((current) => current || account.fullName || "");
      } catch {
        // The form still works; the contact field just stays empty.
      }
    };

    void loadAccount();

    return () => controller.abort();
  }, []);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    setMessage("");
    setError("");
    setIsSubmitting(true);

    // Check authentication before performing form-specific validation.
    const token = localStorage.getItem("authToken");

    if (!token) {
      navigate("/login?sessionExpired=true", {
        replace: true,
      });
      setIsSubmitting(false);
      return;
    }

    const emailPattern =
      /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

    if (
      preferredContact === "email" &&
      !emailPattern.test(contactInfo.trim())
    ) {
      setError(
        "Please enter a valid email address such as name@example.com."
      );
      setIsSubmitting(false);
      return;
    }

    try {
      const response = await fetch(apiUrl("/api/intake"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          clientFullName: fullName,
          parentGuardianName: guardianName,
          contactInfo:
            preferredContact === "email"
              ? contactInfo.trim().toLowerCase()
              : contactInfo.trim(),
          preferredContactMethod: preferredContact,
          overview,
          supportNeeds,
          respiteGoals,
          subjectFocus,
          additionalNotes,
        }),
      });

      if (!response.ok) {
        setError(await readIntakeError(response));
        return;
      }

      setMessage(
        "Your intake information has been submitted successfully."
      );

      // Keep the account name as the starting value after submission.
      setFullName(accountName);
      setGuardianName("");
      setPreferredContact("");
      setOverview("");
      setSupportNeeds("");
      setRespiteGoals("");
      setSubjectFocus("");
      setAdditionalNotes("");
    } catch {
      setError(
        "Unable to connect to the server. Please make sure the backend is running."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="intake-page">
      <div className="intake-shell">
        <nav className="intake-breadcrumb" aria-label="Breadcrumb">
          <Link to="/client-dashboard">
            <span aria-hidden="true">←</span>
            Back to dashboard
          </Link>

          <span className="intake-portal-label">
            <span className="intake-lock" aria-hidden="true">
              ◆
            </span>
            CLIENT PORTAL
          </span>
        </nav>

        <header className="intake-hero">
          <div className="intake-hero-copy">
            <p className="intake-eyebrow">YOUR NEXT STEP</p>

            <h1>Let’s start with what matters to you.</h1>

            <p className="intake-hero-description">
              Share a little about yourself and the support
              you’re looking for. Your responses help Casey
              understand your needs and consider meaningful
              next steps with you.
            </p>

            <div className="intake-hero-meta">
              <span>
                <span aria-hidden="true">◷</span>
                About 5–10 minutes
              </span>

              <span>
                <span aria-hidden="true">✦</span>
                You can take your time
              </span>
            </div>
          </div>

          <div className="intake-hero-art" aria-hidden="true">
            <span className="intake-art-ring intake-art-ring-one" />
            <span className="intake-art-ring intake-art-ring-two" />
            <span className="intake-art-butterfly">🦋</span>
            <span className="intake-art-spark intake-art-spark-one">
              ✦
            </span>
            <span className="intake-art-spark intake-art-spark-two">
              ✧
            </span>
          </div>
        </header>

        <div className="intake-content-grid">
          <aside className="intake-guidance">
            <section className="intake-guidance-card">
              <span className="intake-guidance-icon" aria-hidden="true">
                ♡
              </span>

              <h2>A space to be heard</h2>

              <p>
                There’s no need to have everything figured
                out. Tell us what feels important right now,
                in your own words.
              </p>
            </section>

            <section className="intake-guidance-card intake-guidance-card-soft">
              <span className="intake-guidance-icon" aria-hidden="true">
                ✧
              </span>

              <h2>What happens next?</h2>

              <p>
                Once you submit your information, Casey can
                review your request and follow up using your
                preferred contact method.
              </p>
            </section>

            <p className="intake-guidance-footnote">
              <span aria-hidden="true">🔒</span>
              This form is part of your private client portal.
            </p>
          </aside>

          <section
            className="intake-form-card"
            aria-labelledby="intake-form-title"
          >
            <div className="intake-form-heading">
              <div>
                <p className="intake-form-kicker">INITIAL INTAKE</p>

                <h2 id="intake-form-title">
                  Tell us about your needs
                </h2>

                <p>
                  Fields marked with{" "}
                  <span aria-hidden="true">*</span> are required.
                </p>
              </div>

              <span className="intake-form-mark" aria-hidden="true">
                ✿
              </span>
            </div>

            <form onSubmit={handleSubmit}>
              <fieldset className="intake-fieldset">
                <legend>
                  <span className="intake-section-number">01</span>
                  About you
                </legend>

                <div className="intake-fields-grid">
                  <div className="form-group">
                    <label htmlFor="fullName">
                      Client’s Full Name <span>*</span>
                    </label>

                    <input
                      id="fullName"
                      name="fullName"
                      type="text"
                      autoComplete="name"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="guardianName">
                      Parent / Guardian Name
                    </label>

                    <input
                      id="guardianName"
                      name="guardianName"
                      type="text"
                      autoComplete="off"
                      value={guardianName}
                      onChange={(e) => setGuardianName(e.target.value)}
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="preferredContact">
                      Preferred Contact Method <span>*</span>
                    </label>

                    <select
                      id="preferredContact"
                      name="preferredContact"
                      value={preferredContact}
                      onChange={(e) => setPreferredContact(e.target.value)}
                      required
                    >
                      <option value="">
                        Choose how we should contact you
                      </option>
                      <option value="email">Email</option>
                      <option value="phone">Phone</option>
                      <option value="text">Text message</option>
                    </select>
                  </div>

                  <div className="form-group">
                    <label htmlFor="contactInfo">
                      {preferredContact === "email"
                        ? "Email Address"
                        : preferredContact === "phone" ||
                            preferredContact === "text"
                          ? "Phone Number"
                          : "Email Address or Phone Number"}{" "}
                      <span>*</span>
                    </label>

                    <input
                      id="contactInfo"
                      name="contactInfo"
                      type={
                        preferredContact === "email" ? "email" : "tel"
                      }
                      value={contactInfo}
                      readOnly
                      aria-readonly="true"
                      aria-describedby="contactInfoHelp"
                      className="intake-readonly-input"
                      placeholder={
                        preferredContact
                          ? "Not available on your account"
                          : "Choose a contact method first"
                      }
                      required
                    />

                    <small id="contactInfoHelp">
                      Filled in from your verified account details.
                      It can’t be edited here.
                    </small>
                  </div>
                </div>
              </fieldset>

              <fieldset className="intake-fieldset">
                <legend>
                  <span className="intake-section-number">02</span>
                  Your situation and support needs
                </legend>

                <div className="form-group">
                  <label htmlFor="overview">
                    A little about your situation <span>*</span>
                  </label>

                  <textarea
                    id="overview"
                    name="overview"
                    value={overview}
                    onChange={(e) => setOverview(e.target.value)}
                    placeholder="Tell us what has been happening and what prompted you to reach out."
                    required
                    rows={4}
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="supportNeeds">
                    What kind of support are you looking for?{" "}
                    <span>*</span>
                  </label>

                  <textarea
                    id="supportNeeds"
                    name="supportNeeds"
                    value={supportNeeds}
                    onChange={(e) => setSupportNeeds(e.target.value)}
                    placeholder="Share the areas where you or your family would appreciate support."
                    required
                    rows={4}
                  />
                </div>
              </fieldset>

              <fieldset className="intake-fieldset">
                <legend>
                  <span className="intake-section-number">03</span>
                  A little more detail
                </legend>

                <div className="intake-fields-grid">
                  <div className="form-group">
                    <label htmlFor="respiteGoals">
                      Goals for Respite
                    </label>

                    <textarea
                      id="respiteGoals"
                      name="respiteGoals"
                      value={respiteGoals}
                      onChange={(e) => setRespiteGoals(e.target.value)}
                      placeholder="What would you hope to gain from respite support?"
                      rows={3}
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="subjectFocus">Subject Focus</label>

                    <input
                      id="subjectFocus"
                      name="subjectFocus"
                      type="text"
                      value={subjectFocus}
                      onChange={(e) => setSubjectFocus(e.target.value)}
                      placeholder="If relevant"
                    />
                  </div>

                  <div className="form-group intake-full-width">
                    <label htmlFor="additionalNotes">
                      Anything else you’d like Casey to know?
                    </label>

                    <textarea
                      id="additionalNotes"
                      name="additionalNotes"
                      value={additionalNotes}
                      onChange={(e) => setAdditionalNotes(e.target.value)}
                      placeholder="Any additional context, questions, or details you would like to share."
                      rows={4}
                    />
                  </div>
                </div>
              </fieldset>

              {error && (
                <p
                  className="intake-feedback intake-feedback-error"
                  role="alert"
                >
                  {error}
                </p>
              )}

              {message && (
                <div
                  className="intake-feedback intake-feedback-success"
                  role="status"
                >
                  <strong>{message}</strong>

                  <p>
                    You can return to your dashboard to review
                    your request status.
                  </p>

                  <Link to="/client-dashboard">
                    Return to dashboard →
                  </Link>
                </div>
              )}

              <div className="intake-submit-row">
                <p className="intake-privacy-note">
                  Your information will be submitted to the
                  counselling team so they can review your
                  support request.
                </p>

                <button type="submit" disabled={isSubmitting}>
                  {isSubmitting
                    ? "Submitting your request…"
                    : "Submit intake request"}

                  <span aria-hidden="true">→</span>
                </button>
              </div>
            </form>
          </section>
        </div>

        <footer className="intake-page-footer">
          <span>ESSENTIAL BUTTERFLY</span>
          <span>Support · Growth · Brighter Tomorrows</span>
          <Link to="/client-dashboard">
            Back to your dashboard
          </Link>
        </footer>
      </div>
    </main>
  );
}

export default Intake;
