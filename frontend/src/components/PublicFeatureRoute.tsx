import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { FeatureKey } from "../context/PublicFeatureContext";
import { usePublicFeatures } from "../context/PublicFeatureContext";

export default function PublicFeatureRoute({ feature, children }: { feature: FeatureKey; children: ReactNode }) {
  const { loading, error, refresh, isEnabled } = usePublicFeatures();

  if (loading) return <main className="feature-state" aria-live="polite"><h1>Loading website feature…</h1><p>Please wait while we check availability.</p></main>;
  if (error) return <main className="feature-state" role="alert"><h1>Unable to check feature availability</h1><p>{error}</p><button type="button" onClick={() => void refresh()}>Retry</button></main>;
  if (!isEnabled(feature)) return <main className="feature-state" role="status"><p className="dashboard-eyebrow">ESSENTIAL BUTTERFLY</p><h1>Feature unavailable</h1><p>This feature is not currently being offered. Please return to the homepage to explore what is available.</p><Link to="/" className="feature-home-link">Back to homepage</Link></main>;
  return <>{children}</>;
}
