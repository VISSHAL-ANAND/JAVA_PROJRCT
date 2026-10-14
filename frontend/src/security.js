const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

/**
 * Prevent production browser requests from sending bearer tokens over cleartext HTTP.
 * Loopback HTTP remains available for local production-build previews.
 */
export function isApiUrlSecure(apiBaseUrl, production) {
  if (!production) return true;
  try {
    const url = new URL(apiBaseUrl);
    return url.protocol === "https:" || (url.protocol === "http:" && LOOPBACK_HOSTS.has(url.hostname));
  } catch {
    return false;
  }
}
