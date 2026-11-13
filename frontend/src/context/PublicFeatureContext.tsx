
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

import type { ReactNode } from "react";
import { apiUrl } from "../api/api";

export type FeatureKey =
  | "ABOUT"
  | "SERVICES"
  | "CONTACT"
  | "INTAKE"
  | "NEWSLETTER"
  | "TESTIMONIALS"
  | "UPDATES"
  | "APPOINTMENTS";

export type PublicFeature = {
  featureKey: FeatureKey;
  label: string;
  enabled: boolean;
  displayOrder: number;
};

type FeatureContextType = {
  features: PublicFeature[];
  loading: boolean;
  error: string;
  refresh: () => Promise<void>;
  isEnabled: (key: FeatureKey) => boolean;
};

const FeatureContext =
  createContext<FeatureContextType | null>(null);

export function PublicFeatureProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [features, setFeatures] =
    useState<PublicFeature[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const response = await fetch(apiUrl("/api/features"));

      if (!response.ok) {
        throw new Error("Unable to load website features.");
      }

      const data = await response.json() as PublicFeature[];

      if (!Array.isArray(data)) {
        throw new Error("Invalid website features response.");
      }

      setFeatures(data);
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Unable to load website features."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();

    const handleUpdate = () => {
      void refresh();
    };

    window.addEventListener(
      "public-features-updated",
      handleUpdate
    );

    return () => {
      window.removeEventListener(
        "public-features-updated",
        handleUpdate
      );
    };
  }, [refresh]);

  const value = useMemo<FeatureContextType>(() => ({
    features,
    loading,
    error,
    refresh,

    isEnabled: (key: FeatureKey) => {
      const feature = features.find(
        (item) => item.featureKey === key
      );

      // If a feature record exists, respect the admin setting.
      if (feature) {
        return feature.enabled;
      }

      // Core navigation and newsletter remain available if the backend has
      // not created a visibility record. Optional Sprint 2 features stay off.
      if (["ABOUT", "SERVICES", "CONTACT", "INTAKE", "NEWSLETTER"].includes(key)) {
        return true;
      }

      // Other features remain disabled if no record exists.
      return false;
    },
  }), [features, loading, error, refresh]);

  return (
    <FeatureContext.Provider value={value}>
      {children}
    </FeatureContext.Provider>
  );
}

export function usePublicFeatures() {
  const context = useContext(FeatureContext);

  if (!context) {
    throw new Error(
      "usePublicFeatures must be used inside PublicFeatureProvider."
    );
  }

  return context;
}
