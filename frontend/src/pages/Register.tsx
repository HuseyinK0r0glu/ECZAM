import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import AuthLayout from "../components/layout/AuthLayout";
import { Alert, Button, Field, Input } from "../components/ui";

export default function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try { await register(email, password, displayName || undefined); nav("/dashboard"); }
    catch (err: any) {
      const code = err?.response?.data?.error?.code;
      setError(code === "EMAIL_TAKEN" ? "Bu e-posta zaten kayıtlı." : "Kayıt başarısız. Bilgileri kontrol edin.");
    }
  }

  return (
    <AuthLayout
      title="Kayıt Ol"
      subtitle="Birkaç saniyede hesap oluşturun"
      footer={
        <>
          Zaten hesabın var mı?{" "}
          <Link className="font-semibold text-brand-700 underline-offset-2 hover:underline" to="/login">
            Giriş yap
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-5">
        <Field label="Ad (isteğe bağlı)">
          <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="name" />
        </Field>
        <Field label="E-posta">
          <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        </Field>
        <Field label="Şifre (en az 8 karakter)">
          <Input
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
          />
        </Field>
        {error && <Alert variant="error">{error}</Alert>}
        <Button type="submit" block>Kayıt Ol</Button>
      </form>
    </AuthLayout>
  );
}
