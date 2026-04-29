// Spec 014 — Vercel Edge Function entry.
// Vercel auto-detects this path and produces the deployed lambda. The actual
// handler logic lives in `../index.ts` (preserves test imports). We import
// the default as a named symbol and wrap it so Vercel's bundler treats this
// file as the function entry — `export { default } from "../index"` does
// not surface a callable handler on Vercel Edge (causes invocation timeout).

import handlerImpl from "../index.js";

export const config = { runtime: "edge" };

export default function handler(req: Request): Promise<Response> {
  return handlerImpl(req);
}
