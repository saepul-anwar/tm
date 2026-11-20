import { NextResponse, type NextRequest } from "next/server"

import { SESSION_HINT_COOKIE, isPublicPath } from "@/lib/auth/constants"

// UX redirect only, based on the presence of a non-sensitive hint cookie. The
// httpOnly refresh cookie is scoped to /api/auth and invisible here; real
// authentication and authorization happen in the backend on every API call.
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl
  const hasHint = request.cookies.has(SESSION_HINT_COOKIE)

  if (!isPublicPath(pathname) && !hasHint) {
    return NextResponse.redirect(new URL("/login", request.url))
  }
  if (isPublicPath(pathname) && hasHint) {
    return NextResponse.redirect(new URL("/dashboard", request.url))
  }
  return NextResponse.next()
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
}
