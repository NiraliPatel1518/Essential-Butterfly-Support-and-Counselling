import { useEffect, useState } from "react";
import {
  fetchPublishedContent,
  type ContentPageKey,
  type PublishedContent,
} from "../api/contentApi";

const CACHE_PREFIX = "essential-butterfly:published:";

function readCache(pageKey: ContentPageKey): PublishedContent | null {
  try {
    const stored = sessionStorage.getItem(CACHE_PREFIX + pageKey);
    return stored ? (JSON.parse(stored) as PublishedContent) : null;
  } catch {
    return null;
  }
}

function writeCache(pageKey: ContentPageKey, content: PublishedContent | null) {
  try {
    if (content) {
      sessionStorage.setItem(CACHE_PREFIX + pageKey, JSON.stringify(content));
    } else {
      sessionStorage.removeItem(CACHE_PREFIX + pageKey);
    }
  } catch {
    // Storage is unavailable; the page simply loads fresh each time.
  }
}

/*
 * The text an administrator has published for a page, or null while the
 * website's built-in page is still in use.
 *
 * The built-in page is shown at once. If published text exists it replaces the
 * built-in page as soon as it arrives. The last published copy is remembered
 * for the rest of the visit, so moving between pages does not flash the
 * built-in text first. If the server cannot be reached, the built-in page stays.
 */
export function usePublishedContent(
  pageKey: ContentPageKey | null
): PublishedContent | null {
  const [content, setContent] = useState<PublishedContent | null>(() =>
    pageKey ? readCache(pageKey) : null
  );

  useEffect(() => {
    // No page key: the editor is previewing unsaved wording; nothing to fetch.
    if (!pageKey) return;

    let active = true;

    fetchPublishedContent(pageKey)
      .then((latest) => {
        if (!active) return;
        setContent(latest);
        writeCache(pageKey, latest);
      })
      .catch(() => {
        // Keep whatever is already on screen.
      });

    return () => {
      active = false;
    };
  }, [pageKey]);

  return content;
}
