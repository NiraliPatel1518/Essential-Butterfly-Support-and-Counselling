const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();

// In Docker, browser requests use the same origin and pass through the
// Nginx gateway. During development, Vite proxies /api requests.
// Therefore, the default base URL must be empty—not "/api".
export const API_BASE_URL = (configuredBaseUrl || "").replace(/\/+$/, "");

export function apiUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;

  return API_BASE_URL
    ? `${API_BASE_URL}${normalizedPath}`
    : normalizedPath;
}