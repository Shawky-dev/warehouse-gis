import { createContext, useContext, useState, useCallback, type ReactNode } from "react";

export interface AuthUser {
    email: string;
    name?: string;
    avatarUrl?: string;
}

interface AuthContextValue {
    user: AuthUser | null;
    token: string | null;
    setAuth: (user: AuthUser, token: string) => void;
    logout: () => void;
    isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(() => {
        const stored = localStorage.getItem("auth_user");
        return stored ? JSON.parse(stored) : null;
    });

    const [token, setToken] = useState<string | null>(() =>
        localStorage.getItem("auth_token")
    );

    const setAuth = useCallback((newUser: AuthUser, newToken: string) => {
        setUser(newUser);
        setToken(newToken);
        localStorage.setItem("auth_user", JSON.stringify(newUser));
        localStorage.setItem("auth_token", newToken);
    }, []);

    const logout = useCallback(() => {
        setUser(null);
        setToken(null);
        localStorage.removeItem("auth_user");
        localStorage.removeItem("auth_token");
    }, []);

    return (
        <AuthContext.Provider
            value={{ user, token, setAuth, logout, isAuthenticated: !!token }}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
    return ctx;
}
