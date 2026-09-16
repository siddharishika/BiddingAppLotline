import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth";

export default function RequireAuth({ children }) {
  const { user, ready } = useAuth();
  const location = useLocation();

  if (!ready) {
    return <main className="wrap page-status"><p className="muted">Loading…</p></main>;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}
