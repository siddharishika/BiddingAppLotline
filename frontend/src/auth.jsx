import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { getMe, login as loginRequest, logout as logoutRequest } from "./api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    getMe()
      .then((me) => setUser(me.authenticated ? me : null))
      .catch(() => setUser(null))
      .finally(() => setReady(true));
  }, []);

  const value = useMemo(() => ({
    user,
    ready,
    async login(username, password) {
      const me = await loginRequest(username, password);
      setUser(me);
      return me;
    },
    async logout() {
      await logoutRequest();
      setUser(null);
    },
  }), [user, ready]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
