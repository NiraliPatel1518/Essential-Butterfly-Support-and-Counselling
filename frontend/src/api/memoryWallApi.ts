import { apiUrl } from "./api";
import { ApiError, requestJson, send, toApiError } from "./contentApi";

/*
 * Everything the Memory Wall screens say to the backend (UC-12). Addresses
 * always go through apiUrl(), so no server address is written here. Every
 * function either returns data or throws an ApiError whose message is safe to
 * show to a person (status 401 means the administrator's sign-in expired).
 */

export type PublicPhoto = {
  id: number;
  altText: string;
  uploadedAt: string;
  // "October 12, 2026"
  uploadedOn: string;
};

// The public wall is grouped by the month and year of upload.
export type WallMonth = {
  key: string;
  label: string;
  photos: PublicPhoto[];
};

export type PhotoStatus = "PENDING_REVIEW" | "PUBLISHED" | "UNPUBLISHED";

export type AdminPhoto = {
  id: number;
  status: PhotoStatus;
  source: "ADMIN_UPLOAD" | "TESTIMONIAL";
  uploadedBy: string;
  uploadedAt: string;
  publishedAt: string | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  description: string | null;
  altText: string;
  testimonialStatus: string | null;
  canPublish: boolean;
  publishBlockedReason: string | null;
};

export type WallSummary = {
  totalPhotos: number;
  maxPhotos: number;
  remainingUploads: number;
  publishedPhotos: number;
  maxPublishedPhotos: number;
  storageUsedBytes: number;
  maxFileSizeBytes: number;
  maxFileSizeLabel: string;
  allowedExtensions: string[];
  acceptedFormats: string;
};

export type AdminOverview = {
  photos: AdminPhoto[];
  summary: WallSummary;
};

function adminHeaders(extra: Record<string, string> = {}) {
  return {
    Authorization: `Bearer ${localStorage.getItem("adminToken")}`,
    ...extra,
  };
}

const JSON_TYPE = { "Content-Type": "application/json" };

// ------------------------------------------------------------------ public

export function fetchWall(): Promise<WallMonth[]> {
  return requestJson<WallMonth[]>("/api/memory-wall");
}

// The picture of a published photo. Nothing else is ever served here.
export function publicImageUrl(id: number): string {
  return apiUrl(`/api/memory-wall/photos/${id}/image`);
}

// ------------------------------------------------------------------- admin

export function fetchOverview(): Promise<AdminOverview> {
  return requestJson<AdminOverview>("/api/admin/memory-wall", {
    headers: adminHeaders(),
  });
}

/*
 * The administrator's pictures need the sign-in header, which an <img> tag
 * cannot send, so the picture is fetched and shown from a temporary address.
 * The caller revokes that address with URL.revokeObjectURL when done.
 */
export async function fetchAdminImage(id: number): Promise<string> {
  const response = await send(`/api/admin/memory-wall/photos/${id}/image`, {
    headers: adminHeaders(),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return URL.createObjectURL(await response.blob());
}

// One photo per request, so every file gets its own clear success or error.
export function uploadPhoto(file: File): Promise<AdminPhoto> {
  const form = new FormData();

  form.append("file", file);

  return requestJson<AdminPhoto>("/api/admin/memory-wall/photos", {
    method: "POST",
    headers: adminHeaders(),
    body: form,
  });
}

export function savePhotoDescription(
  id: number,
  description: string
): Promise<AdminPhoto> {
  return requestJson<AdminPhoto>(`/api/admin/memory-wall/photos/${id}`, {
    method: "PUT",
    headers: adminHeaders(JSON_TYPE),
    body: JSON.stringify({ description }),
  });
}

// Save changes: these photos are on the public wall; every other photo is not.
export function saveSelection(photoIds: number[]): Promise<AdminOverview> {
  return requestJson<AdminOverview>("/api/admin/memory-wall/selection", {
    method: "PUT",
    headers: adminHeaders(JSON_TYPE),
    body: JSON.stringify({ photoIds }),
  });
}

export async function deletePhoto(id: number): Promise<void> {
  const response = await send(`/api/admin/memory-wall/photos/${id}`, {
    method: "DELETE",
    headers: adminHeaders(),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
}

export { ApiError };
