// Spec 014 — Edge Function entry point.
// Auth gate (Phase B) is live. Router/handlers (Phase C+) are still stubbed.

import { verifyJwt } from "./lib/auth.js";
import { UnauthorizedError, errorResponse, UNAUTHORIZED_MESSAGES } from "./lib/errors.js";

export const config = { runtime: "edge" };

export interface RequestContext {
  userId: string;
}

export default async function handler(req: Request): Promise<Response> {
  if (req.method !== "POST") {
    return errorResponse("INTERNAL", "method not allowed", "", 405);
  }

  // Phase B — auth gate. Failures map to HTTP 401 with the closed-enum
  // UNAUTHORIZED message. requestId is "" because we have not parsed the
  // body yet (auth runs first per FR-014-007).
  let userId: string;
  try {
    const verified = await verifyJwt(req.headers.get("Authorization"));
    userId = verified.sub;
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      return errorResponse("UNAUTHORIZED", e.publicMessage, "", 401);
    }
    return errorResponse(
      "UNAUTHORIZED",
      UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
      "",
      401,
    );
  }

  // Phase C+ wires the router here. Until then, every authenticated
  // request continues to return 501-equivalent INTERNAL so callers cannot
  // mistake the gate for a working handler.
  void userId;
  return errorResponse("INTERNAL", "not yet implemented", "", 200);
}
