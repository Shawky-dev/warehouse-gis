import { useState } from "react";
import { login, type LoginRequest } from "./api";

export const useLogin = () => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const doLogin = async (data: LoginRequest) => {
        setLoading(true);
        setError(null);

        try {
            const result = await login(data);
            return result;
        } catch (err: any) {
            setError(err.message);
            throw err;
        } finally {
            setLoading(false);
        }
    };

    return { doLogin, loading, error };
};
