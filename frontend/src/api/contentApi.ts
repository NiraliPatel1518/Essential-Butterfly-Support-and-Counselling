import { apiUrl } from "./api";

/*
 * Everything the website-content screens say to the backend (UC-09).
 * Addresses always go through apiUrl(), so no server address is written here.
 *
 * Every function either returns data or throws an ApiError whose message is
 * already safe to show to a person: the server's own explanation for a
 * mistake the administrator can fix, a friendly sentence for server or
 * network problems, and a status of 401 when the sign-in has expired.
 */

export type ContentPageKey = "services" | "about" | "privacy";

export type PublishedContent = {
  pageKey: string;
  title: string;
  body: string;
  publishedAt: string;
};

export type ContentPageAdmin = {
  pageKey: ContentPageKey;
  label: string;
  published: boolean;
  hasUnpublishedChanges: boolean;
  publishedTitle: string | null;
  publishedBody: string | null;
  draftTitle: string | null;
  draftBody: string | null;
  publishedAt: string | null;
  updatedAt: string;
};

export type PublicResource = {
  id: number;
  title: string;
  description: string;
  category: string;
  // "FILE" when a document or image is attached, otherwise "LINK".
  resourceType: "LINK" | "FILE";
  linkUrl: string | null;
  // Every web link of the resource, first one first (up to five).
  links: string[];
  fileName: string | null;
  fileContentType: string | null;
  fileSize: number | null;
  altText: string | null;
  publishedAt: string | null;
};

export type AdminResource = PublicResource & {
  status: "DRAFT" | "PUBLISHED";
  createdAt: string;
  updatedAt: string;
};

export type ResourceFields = {
  title: string;
  description: string;
  category: string;
  altText: string;
  // Web links, one per line (up to five). May be empty when a file is attached.
  linkUrls: string;
  // Remove the document or image that is attached now.
  removeFile: boolean;
  publish: boolean;
};

export type UploadRules = {
  maxFileSizeBytes: number;
  maxFileSizeLabel: string;
  allowedExtensions: string[];
  acceptedFormats: string;
};

export type AuditEntry = {
  id: number;
  occurredAt: string;
  action: string;
  entityType: string;
  entityId: number | null;
  actor: string | null;
  details: string;
};

export const FRIENDLY_SERVER_ERROR =
  "We could not complete that right now. Nothing was changed and what you entered is still on screen. Please try again in a moment.";

export const SESSION_EXPIRED_MESSAGE =
  "Your session has expired. Please sign in again.";

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function readServerMessage(response: Response): Promise<string> {
  const body = await response.text();

  if (!body) {
    return "";
  }

  try {
    const parsed = JSON.parse(body) as { message?: string; error?: string };
    return parsed.message || parsed.error || "";
  } catch {
    return "";
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  if (response.status === 401) {
    return new ApiError(SESSION_EXPIRED_MESSAGE, 401);
  }

  if (response.status >= 500) {
    return new ApiError(FRIENDLY_SERVER_ERROR, response.status);
  }

  const message = await readServerMessage(response);

  return new ApiError(message || FRIENDLY_SERVER_ERROR, response.status);
}

function adminHeaders(extra: Record<string, string> = {}) {
  return {
    Authorization: `Bearer ${localStorage.getItem("adminToken")}`,
    ...extra,
  };
}

async function send(path: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(apiUrl(path), init);
  } catch {
    // The browser could not reach the server at all.
    throw new ApiError(FRIENDLY_SERVER_ERROR, 0);
  }
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await send(path, init);

  if (!response.ok) {
    throw await toApiError(response);
  }

  return (await response.json()) as T;
}

// ------------------------------------------------------------- public pages

export async function fetchPublishedContent(
  pageKey: ContentPageKey
): Promise<PublishedContent | null> {
  const response = await send(`/api/content/${pageKey}`);

  if (!response.ok || response.status === 204) {
    return null;
  }

  const data = (await response.json()) as Partial<PublishedContent> | null;

  if (
    !data ||
    typeof data.title !== "string" ||
    typeof data.body !== "string"
  ) {
    return null;
  }

  return data as PublishedContent;
}

export function fetchPublicResources(): Promise<PublicResource[]> {
  return requestJson<PublicResource[]>("/api/resources");
}

export function resourceDownloadUrl(id: number): string {
  return apiUrl(`/api/resources/${id}/download`);
}

// -------------------------------------------------------------- admin pages

export function fetchAdminPages(): Promise<ContentPageAdmin[]> {
  return requestJson<ContentPageAdmin[]>("/api/admin/content", {
    headers: adminHeaders(),
  });
}

export function saveAdminPage(
  pageKey: ContentPageKey,
  page: { title: string; body: string; publish: boolean }
): Promise<ContentPageAdmin> {
  return requestJson<ContentPageAdmin>(`/api/admin/content/${pageKey}`, {
    method: "PUT",
    headers: adminHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(page),
  });
}

// -------------------------------------------------------- admin resources

export function fetchAdminResources(): Promise<AdminResource[]> {
  return requestJson<AdminResource[]>("/api/admin/resources", {
    headers: adminHeaders(),
  });
}

export function fetchUploadRules(): Promise<UploadRules> {
  return requestJson<UploadRules>("/api/admin/resources/upload-rules", {
    headers: adminHeaders(),
  });
}

/*
 * Adds (id is null) or updates a resource. The file, if any, travels in the
 * same request as the text fields. The browser sets the multipart boundary
 * itself, so no Content-Type header is written here.
 */
export function saveResource(
  id: number | null,
  fields: ResourceFields,
  file: File | null
): Promise<AdminResource> {
  const form = new FormData();

  form.append("title", fields.title);
  form.append("description", fields.description);
  form.append("category", fields.category);
  form.append("altText", fields.altText);
  form.append("publish", String(fields.publish));

  form.append("linkUrls", fields.linkUrls);
  form.append("removeFile", String(fields.removeFile));

  if (file) {
    form.append("file", file);
  }

  return requestJson<AdminResource>(
    id === null ? "/api/admin/resources" : `/api/admin/resources/${id}`,
    {
      method: id === null ? "POST" : "PUT",
      headers: adminHeaders(),
      body: form,
    }
  );
}

export async function removeResource(id: number): Promise<void> {
  const response = await send(`/api/admin/resources/${id}`, {
    method: "DELETE",
    headers: adminHeaders(),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
}

// ---------------------------------------------------------------- audit log

export function fetchAuditLog(limit = 50): Promise<AuditEntry[]> {
  return requestJson<AuditEntry[]>(`/api/admin/audit-log?limit=${limit}`, {
    headers: adminHeaders(),
  });
}
