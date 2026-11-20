/**
 * Name of the non-sensitive "a session probably exists" cookie. The real refresh
 * cookie is httpOnly and scoped to /api/auth, so page requests (and the proxy)
 * never see it. This hint is UX only; authorization is enforced by the backend.
 */
export const SESSION_HINT_COOKIE = "st_session"

export const SESSION_HINT_MAX_AGE_SECONDS = 7 * 24 * 60 * 60

export const PUBLIC_PATHS = ["/login", "/register"] as const

export const isPublicPath = (pathname: string) =>
  (PUBLIC_PATHS as readonly string[]).includes(pathname)
