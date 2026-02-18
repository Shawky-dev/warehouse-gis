import { useState } from "react";
import { useLogin } from "./useLogin";

type Props = {}

const LoginPage = (props: Props) => {
const { doLogin, loading, error } = useLogin();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const { token } = await doLogin({ email, password });
      // handle storing token later
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