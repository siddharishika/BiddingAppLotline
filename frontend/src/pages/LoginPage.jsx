import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { useToast } from "../toast";

export default function LoginPage() {
  const { login } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const from = location.state?.from || "/auctions";

  async function onSubmit(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await login(username.trim(), password);
      toast.success("Signed in.");
      navigate(from, { replace: true });
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="wrap narrow">
      <section className="auth-card">
        <p className="eyebrow">Members only</p>
        <h1>Sign in to bid</h1>
        <p className="lede">
          Only registered accounts can place bids or list lots.
        </p>
        <form className="stack" onSubmit={onSubmit}>
          <label>Username
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
          </label>
          <label>Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
          </label>
          <button className="btn btn-gold" type="submit" disabled={busy}>
            {busy ? "Signing in…" : "Enter the room"}
          </button>
        </form>
        <p className="muted">No paddle yet? <Link to="/register">Create an account</Link></p>
      </section>
    </main>
  );
}
