// Spec 014 — Edge Function entry point. Phase A skeleton: returns 501 for
// every authenticated path. Auth gate, router, and handlers land in
// Phases B–F.

export const config = { runtime: "edge" };

const NOT_IMPLEMENTED_BODY = {
  type: "error",
  code: "INTERNAL",
  message: "not yet implemented",
} as const;

const METHOD_NOT_ALLOWED_BODY = {
  type: "error",
  code: "INTERNAL",
  message: "method not allowed",
} as const;

export default async function handler(req: Request): Promise<Response> {
  if (req.method !== "POST") {
    return new Response(JSON.stringify(METHOD_NOT_ALLOWED_BODY), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }
  return new Response(JSON.stringify(NOT_IMPLEMENTED_BODY), {
    status: 501,
    headers: { "Content-Type": "application/json" },
  });
}
