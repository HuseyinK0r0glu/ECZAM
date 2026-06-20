import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import AuthLayout from "../components/layout/AuthLayout";
import { Alert, Button, Field, Input } from "../components/ui";

export default function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try { await login(email, password); nav("/dashboard"); }
    catch { setError("E-posta veya şifre hatalı."); }
  }

  return (
    <AuthLayout
      title="Giriş Yap"
      subtitle="Hesabınıza erişin"
      footer={
        <>
          Hesabın yok mu?{" "}
          <Link className="font-semibold text-brand-700 underline-offset-2 hover:underline" to="/register">
            Kayıt ol
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-5" aria-describedby={error ? "err" : undefined}>
        <Field label="E-posta">
          <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        </Field>
        <Field label="Şifre">
          <Input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        </Field>
        {error && <Alert variant="error" id="err">{error}</Alert>}
        <Button type="submit" block>Giriş Yap</Button>
      </form>
    </AuthLayout>
  );
}
