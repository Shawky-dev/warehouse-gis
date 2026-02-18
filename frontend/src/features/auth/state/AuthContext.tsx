import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { login as loginApi, logout as logoutApi, refresh as refreshApi } from "@/features/auth/api/authApi";
import {
  clearAuthSessionManagerConfig,
  configureAuthSessionManager,
  setAccessToken,
} from "@/features/auth/session/authSessionManager";
import type { AuthResponse, AuthState, LoginRequest } from "@/features/auth/types";

interface AuthContextValue extends AuthState {
  isAuthenticated: boolean;
  hasRole: (role: string) => boolean;
  bootstrapSession: () => Promise<void>;
  login: (payload: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  setSession: (session: AuthResponse) => void;
  clearSession: () => void;
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
  const [state, setState] = useState<AuthState>(initialState);

  const setSession = useCallback((session: AuthResponse) => {
    setAccessToken(session.accessToken);
    setState({
      accessToken: session.accessToken,
      user: session.user,
      status: "authenticated",
    });
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setState({
      accessToken: null,
      user: null,
      status: "unauthenticated",
    });
  }, []);

  const bootstrapSession = useCallback(async () => {
    setState((current) => ({ ...current, status: "loading" }));

    try {
      const session = await refreshApi();
      setSession(session);
    } catch {
      clearSession();
    }
  }, [clearSession, setSession]);

  const login = useCallback(
    async (payload: LoginRequest) => {
      setState((current) => ({ ...current, status: "loading" }));

      try {
        const session = await loginApi(payload);
        setSession(session);
      } catch (error) {
        clearSession();
        throw error;
      }
    },
    [clearSession, setSession]
  );

  const logout = useCallback(async () => {
    try {
      await logoutApi();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  useEffect(() => {
    configureAuthSessionManager({
      refreshSession: refreshApi,
      onSessionUpdate: setSession,
      onUnauthorized: clearSession,
    });

    return () => {
      clearAuthSessionManagerConfig();
    };
  }, [clearSession, setSession]);

  useEffect(() => {
    void bootstrapSession();
  }, [bootstrapSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      isAuthenticated: state.status === "authenticated" && !!state.accessToken,
      hasRole: (role: string) => !!state.user?.roles.includes(role),
      bootstrapSession,
      login,
      logout,
      setSession,
      clearSession,
    }),
    [bootstrapSession, clearSession, login, logout, setSession, state]
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
