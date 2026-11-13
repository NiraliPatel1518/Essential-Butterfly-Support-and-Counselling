import { useMemo } from "react";
import type { ContentPageKey } from "../api/contentApi";
import {
  resolvePageContent,
  type PageContent,
  type ServiceItem,
} from "../content/pageLayouts";
import { usePublishedContent } from "./usePublishedContent";

export type PageText = {
  // The wording for one named slot of the page.
  text: (key: string) => string;
  services: ServiceItem[];
};

/*
 * The wording a page shows: what an administrator has published, slot by
 * slot, otherwise the page's own original wording. The page itself decides
 * where every piece of text goes, so its design never changes.
 *
 * "preview" lets the editor show unsaved wording inside the real page.
 */
export function usePageContent(
  pageKey: ContentPageKey,
  preview?: PageContent
): PageText {
  const published = usePublishedContent(preview ? null : pageKey);

  const content = useMemo(
    () => preview ?? resolvePageContent(pageKey, published?.body),
    [preview, pageKey, published]
  );

  return {
    text: (key: string) => content.fields[key] ?? "",
    services: content.services ?? [],
  };
}
