/*
 * Keeps what an administrator has typed for a short time, so it is not lost
 * when the sign-in expires. It lives in sessionStorage (cleared when the tab
 * closes) and is removed as soon as the content is saved.
 *
 * Browsers can refuse storage (private windows, blocked site data), so every
 * call is wrapped and the editor works the same without it.
 */
const PREFIX = "essential-butterfly:draft:";

export function saveDraft(key: string, value: unknown): void {
  try {
    sessionStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch {
    // Storage is unavailable; the typed text simply is not kept.
  }
}

export function loadDraft<T>(key: string): T | null {
  try {
    const stored = sessionStorage.getItem(PREFIX + key);
    return stored ? (JSON.parse(stored) as T) : null;
  } catch {
    return null;
  }
}

export function clearDraft(key: string): void {
  try {
    sessionStorage.removeItem(PREFIX + key);
  } catch {
    // Nothing to clear.
  }
}
