import { SESSION_HINT_COOKIE, SESSION_HINT_MAX_AGE_SECONDS } from "./constants"

export function setSessionHint(): void {
  if (typeof document === "undefined") return
  const secure = window.location.protocol === "https:" ? "; Secure" : ""
  document.cookie = `${SESSION_HINT_COOKIE}=1; Path=/; Max-Age=${SESSION_HINT_MAX_AGE_SECONDS}; SameSite=Strict${secure}`
}

export function clearSessionHint(): void {
  if (typeof document === "undefined") return
  document.cookie = `${SESSION_HINT_COOKIE}=; Path=/; Max-Age=0; SameSite=Strict`
}

export function hasSessionHint(): boolean {
  if (typeof document === "undefined") return false
  return document.cookie.split("; ").some((part) => part.startsWith(`${SESSION_HINT_COOKIE}=1`))
}
