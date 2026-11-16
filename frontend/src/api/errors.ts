export class FriendlyApiError extends Error {
  status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = "FriendlyApiError";
    this.status = status;
  }
}

const STATUS_MESSAGES: Record<number, string> = {
  400: "Some information was not accepted. Please review the form and try again.",
  401: "Your session has expired. Please sign in again.",
  403: "You do not have permission to complete this action.",
  404: "The requested information could not be found.",
  408: "The request took too long. Please try again.",
  409: "This request conflicts with information already on file.",
  413: "The selected file is too large. Please choose a smaller file.",
  429: "Too many requests were sent. Please wait a moment and try again.",
  500: "Something went wrong on our side. Please try again shortly.",
  502: "The service is temporarily unavailable. Please try again shortly.",
  503: "The service is temporarily unavailable. Please try again shortly.",
  504: "The service took too long to respond. Please try again.",
};

function isUsefulMessage(value: unknown): value is string {
  if (typeof value !== "string") return false;
  const message = value.trim();
  if (!message) return false;

  return !/^(error\s*)?\d{3}(\s|$)|request failed\s*\(?\d{3}\)?/i.test(
    message,
  );
}

export async function readFriendlyError(
  response: Response,
  fallback = "We could not complete your request. Please try again.",
): Promise<string> {
  try {
    const text = await response.text();

    if (text) {
      try {
        const body = JSON.parse(text) as Record<string, unknown>;
        const candidate = body.message ?? body.error ?? body.detail;
        if (isUsefulMessage(candidate)) return candidate.trim();
      } catch {
        if (isUsefulMessage(text)) return text.trim();
      }
    }
  } catch {
    // A malformed error response should never expose a technical parsing error.
  }

  return STATUS_MESSAGES[response.status] || fallback;
}

export function networkErrorMessage(error: unknown): string {
  if (error instanceof FriendlyApiError) return error.message;

  if (error instanceof TypeError) {
    return "We cannot connect to the service right now. Check your connection and try again.";
  }

  if (error instanceof Error && isUsefulMessage(error.message)) {
    return error.message;
  }

  return "We could not complete your request. Please try again.";
}

