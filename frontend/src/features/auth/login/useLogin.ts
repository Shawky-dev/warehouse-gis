import { useState } from "react";
import { useAuth } from "@/features/auth/context/AuthContext";
import type { LoginRequest } from "@/features/auth/types";
import { useI18n } from "@/i18n";

export const useLogin = () => {
  const { login } = useAuth();
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const doLogin = async (payload: LoginRequest) => {
    setLoading(true);
    setError(null);

    try {
      await login(payload);
    } catch (unknownError) {
      const errorMessage = unknownError instanceof Error ? unknownError.message : t("login.fallbackError");
      setError(errorMessage);
      throw unknownError;
    } finally {
      setLoading(false);
    }
  };

  return { doLogin, loading, error };
};
