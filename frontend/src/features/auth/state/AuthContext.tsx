import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useLocation } from "react-router-dom";
import { landlordLogin, landlordLogout, landlordRefresh } from "@/features/auth/landlord/api/authApi";
import { tenantLogin, tenantLogout, tenantRefresh } from "@/features/auth/tenant/api/authApi";
import {
  clearAuthSessionManagerConfig,
  configureAuthSessionManager,
} from "@/features/auth/session/authSessionManager";
import {
  parseScopeFromPathname,
  toScopeKey,
} from "@/features/auth/shared/scope";
import type { AuthResponse, AuthScope, AuthState, LoginRequest } from "@/features/auth/shared/types";

interface AuthContextValue extends AuthState {
  scope: AuthScope | null;
  isAuthenticated: boolean;
  hasRole: (role: string) => boolean;
  hasPermission: (permission: string) => boolean;
  hasAnyPermission: (permissions: string[]) => boolean;
  hasAllPermissions: (permissions: string[]) => boolean;
  bootstrapSession: () => Promise<void>;
  login: (payload: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  setSession: (session: AuthResponse) => void;
  clearSession: () => void;
  getScopeState: (scope: AuthScope) => AuthState;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function initialState(): AuthState {
  return {
    accessToken: null,
    user: null,
    status: "idle",
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const [sessionsByScopeKey, setSessionsByScopeKey] = useState<Record<string, AuthState>>({});

  const activeScope = useMemo(
    () => parseScopeFromPathname(location.pathname),
    [location.pathname]
  );

  const activeScopeKey = useMemo(
    () => (activeScope ? toScopeKey(activeScope) : null),
    [activeScope]
  );

  const sessionsRef = useRef(sessionsByScopeKey);
  const activeScopeRef = useRef<AuthScope | null>(activeScope);

  useEffect(() => {
    sessionsRef.current = sessionsByScopeKey;
  }, [sessionsByScopeKey]);

  useEffect(() => {
    activeScopeRef.current = activeScope;
  }, [activeScope]);

  const getScopeStateSnapshot = useCallback((scope: AuthScope): AuthState => {
    const scopeKey = toScopeKey(scope);
    return sessionsRef.current[scopeKey] ?? initialState();
  }, []);

  const getScopeState = useCallback(
    (scope: AuthScope): AuthState => {
      const scopeKey = toScopeKey(scope);
      return sessionsByScopeKey[scopeKey] ?? initialState();
    },
    [sessionsByScopeKey]
  );

  const updateScopeState = useCallback(
    (scope: AuthScope, nextState: AuthState) => {
      const scopeKey = toScopeKey(scope);
      setSessionsByScopeKey((currentState) => ({
        ...currentState,
        [scopeKey]: nextState,
      }));
    },
    []
  );

  const refreshForScope = useCallback(async (scope: AuthScope): Promise<AuthResponse> => {
    if (scope.kind === "landlord") {
      return landlordRefresh();
    }
    return tenantRefresh(scope.slug);
  }, []);

  const loginForScope = useCallback(
    async (scope: AuthScope, payload: LoginRequest): Promise<AuthResponse> => {
      if (scope.kind === "landlord") {
        return landlordLogin(payload);
      }
      return tenantLogin(scope.slug, payload);
    },
    []
  );

  const logoutForScope = useCallback(async (scope: AuthScope): Promise<void> => {
    if (scope.kind === "landlord") {
      await landlordLogout();
      return;
    }
    await tenantLogout(scope.slug);
  }, []);

  const setSessionForScope = useCallback(
    (scope: AuthScope, session: AuthResponse) => {
      updateScopeState(scope, {
        accessToken: session.accessToken,
        user: session.user,
        status: "authenticated",
      });
    },
    [updateScopeState]
  );

  const clearSessionForScope = useCallback(
    (scope: AuthScope) => {
      updateScopeState(scope, {
        accessToken: null,
        user: null,
        status: "unauthenticated",
      });
    },
    [updateScopeState]
  );

  const setSession = useCallback(
    (session: AuthResponse) => {
      const scope = activeScopeRef.current;
      if (!scope) {
        throw new Error("Cannot set session without an active scope");
      }
      setSessionForScope(scope, session);
    },
    [setSessionForScope]
  );

  const clearSession = useCallback(() => {
    const scope = activeScopeRef.current;
    if (!scope) {
      return;
    }
    clearSessionForScope(scope);
  }, [clearSessionForScope]);

  const bootstrapSession = useCallback(async () => {
    const scope = activeScopeRef.current;
    if (!scope) {
      return;
    }

    updateScopeState(scope, {
      ...getScopeStateSnapshot(scope),
      status: "loading",
    });

    try {
      const session = await refreshForScope(scope);
      setSessionForScope(scope, session);
    } catch {
      clearSessionForScope(scope);
    }
  }, [clearSessionForScope, getScopeStateSnapshot, refreshForScope, setSessionForScope, updateScopeState]);

  const login = useCallback(
    async (payload: LoginRequest) => {
      const scope = activeScopeRef.current;
      if (!scope) {
        throw new Error("Cannot login without an active scope");
      }

      updateScopeState(scope, {
        ...getScopeStateSnapshot(scope),
        status: "loading",
      });

      try {
        const session = await loginForScope(scope, payload);
        setSessionForScope(scope, session);
      } catch (error) {
        clearSessionForScope(scope);
        throw error;
      }
    },
    [clearSessionForScope, getScopeStateSnapshot, loginForScope, setSessionForScope, updateScopeState]
  );

  const logout = useCallback(async () => {
    const scope = activeScopeRef.current;
    if (!scope) {
      return;
    }

    try {
      await logoutForScope(scope);
    } finally {
      clearSessionForScope(scope);
    }
  }, [clearSessionForScope, logoutForScope]);

  useEffect(() => {
    configureAuthSessionManager({
      getActiveScope: () => activeScopeRef.current,
      getAccessTokenForScope: (scope) => getScopeStateSnapshot(scope).accessToken,
      refreshSession: refreshForScope,
      onSessionUpdate: setSessionForScope,
      onUnauthorized: clearSessionForScope,
    });

    return () => {
      clearAuthSessionManagerConfig();
    };
  }, [clearSessionForScope, getScopeStateSnapshot, refreshForScope, setSessionForScope]);

  useEffect(() => {
    if (!activeScope) {
      return;
    }

    const activeState = getScopeState(activeScope);
    if (activeState.status === "idle") {
      void bootstrapSession();
    }
  }, [activeScope, bootstrapSession, getScopeState]);

  const activeState = useMemo<AuthState>(() => {
    if (!activeScopeKey) {
      return {
        accessToken: null,
        user: null,
        status: "unauthenticated",
      };
    }
    return sessionsByScopeKey[activeScopeKey] ?? initialState();
  }, [activeScopeKey, sessionsByScopeKey]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...activeState,
      scope: activeScope,
      isAuthenticated: activeState.status === "authenticated" && !!activeState.accessToken,
      hasRole: (role: string) => !!activeState.user?.roles.includes(role),
      hasPermission: (permission: string) => !!activeState.user?.permissions.includes(permission),
      hasAnyPermission: (permissions: string[]) =>
        permissions.some((permission) => !!activeState.user?.permissions.includes(permission)),
      hasAllPermissions: (permissions: string[]) =>
        permissions.every((permission) => !!activeState.user?.permissions.includes(permission)),
      bootstrapSession,
      login,
      logout,
      setSession,
      clearSession,
      getScopeState,
    }),
    [activeScope, activeState, bootstrapSession, clearSession, getScopeState, login, logout, setSession]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within <AuthProvider>");
  }
  return context;
}
