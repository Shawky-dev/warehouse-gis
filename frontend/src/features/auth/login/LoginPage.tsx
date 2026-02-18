import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useLogin } from "./useLogin";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";

type Props = {}

const LoginPage = (props: Props) => {
  const { doLogin, loading, error } = useLogin();
  const { setAuth } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const { token } = await doLogin({ email, password });
      setAuth({ email }, token);
      navigate(PATHS.LANDLORD.ROOT, { replace: true });
    } catch (err: any) {
      console.error("Login error:", err);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {error && <div>{error}</div>}
      <input
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <button disabled={loading}>Login</button>
    </form>
  );
}

export default LoginPage