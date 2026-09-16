import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { register } from "../api";
import { useToast } from "../toast";

export default function RegisterPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState({ username: "", email: "", password: "" });
  const [busy, setBusy] = useState(false);

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await register(form);
      toast.success("Account created. Sign in to list a lot or place a bid.");
      navigate("/login", { replace: true });
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="wrap narrow">
      <section className="auth-card">
        <p className="eyebrow">New paddle</p>
        <h1>Register</h1>
        <p className="lede">Your email stays private. Other bidders only see the current highest bid, not who placed it.</p>
        <form className="stack" onSubmit={onSubmit}>
          <label>Username
            <input value={form.username} onChange={(event) => update("username", event.target.value)} autoComplete="username" required />
          </label>
          <label>Email
            <input type="email" value={form.email} onChange={(event) => update("email", event.target.value)} autoComplete="email" required />
          </label>
          <label>Password
            <input type="password" value={form.password} onChange={(event) => update("password", event.target.value)} autoComplete="new-password" required />
          </label>
          <button className="btn btn-gold" type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create account"}
          </button>
        </form>
        <p className="muted">Already registered? <Link to="/login">Sign in</Link></p>
      </section>
    </main>
  );
}
