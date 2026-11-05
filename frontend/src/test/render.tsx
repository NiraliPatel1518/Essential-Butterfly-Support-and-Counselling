import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import type { ReactElement } from "react";
import { PublicFeatureProvider } from "../context/PublicFeatureContext";

function LocationProbe() {
  const location = useLocation();
  return <p>Landed on {location.pathname}{location.search}</p>;
}

export function renderApp(path: string, element: ReactElement) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PublicFeatureProvider>
        <Routes>
          <Route path={path.split("?")[0]} element={element} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </PublicFeatureProvider>
    </MemoryRouter>
  );
}

export function jsonResponse(data: unknown, ok = true, status = ok ? 200 : 400) {
  return Promise.resolve({ ok, status, json: () => Promise.resolve(data), text: () => Promise.resolve(typeof data === "string" ? data : JSON.stringify(data)) });
}
