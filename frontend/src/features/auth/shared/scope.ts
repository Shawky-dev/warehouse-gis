import type { AuthScope } from "@/features/auth/shared/types";

const LANDLORD_PREFIX = "/landlord";

export function normalizeTenantSlug(slug: string): string {
  return slug.trim().toLowerCase();
}

export function toScopeKey(scope: AuthScope): string {
  if (scope.kind === "landlord") {
    return "landlord";
  }
  return `tenant:${normalizeTenantSlug(scope.slug)}`;
}

export function parseScopeFromPathname(pathname: string): AuthScope | null {
  if (!pathname || pathname === "/") {
    return null;
  }

  if (pathname === LANDLORD_PREFIX || pathname.startsWith(`${LANDLORD_PREFIX}/`)) {
    return { kind: "landlord" };
  }

  const normalizedPath = pathname.startsWith("/") ? pathname.substring(1) : pathname;
  if (!normalizedPath) {
    return null;
  }

  const firstSegment = normalizedPath.split("/")[0];
  if (!firstSegment) {
    return null;
  }

  return { kind: "tenant", slug: normalizeTenantSlug(firstSegment) };
}

export function scopeRootPath(scope: AuthScope): string {
  if (scope.kind === "landlord") {
    return "/landlord";
  }
  return `/${normalizeTenantSlug(scope.slug)}`;
}

export function scopeLoginPath(scope: AuthScope): string {
  if (scope.kind === "landlord") {
    return "/landlord/auth/login";
  }
  return `/${normalizeTenantSlug(scope.slug)}/auth/login`;
}

export function isScopePath(pathname: string, scope: AuthScope): boolean {
  if (scope.kind === "landlord") {
    return pathname === "/landlord" || pathname.startsWith("/landlord/");
  }
  const slugPrefix = `/${normalizeTenantSlug(scope.slug)}`;
  return pathname === slugPrefix || pathname.startsWith(`${slugPrefix}/`);
}
