import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth";
import { ToastViewport } from "../toast";

export default function Layout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname, user]);

  function closeMenu() {
    setMenuOpen(false);
  }

  return (
    <>
      <ToastViewport />
      <header className="site-header">
        <div className="wrap header-inner">
          <div className="header-top">
            <Link className="brand" to="/" onClick={closeMenu}>
              <span className="brand-mark">L</span>
              <strong>Lotline</strong>
            </Link>
            <div className="header-actions">
              {user ? (
                <>
                  <span className="who">{user.username}</span>
                  <button className="btn btn-ghost btn-compact" type="button" onClick={logout}>Sign out</button>
                </>
              ) : (
                <>
                  <Link className="btn btn-ghost btn-compact header-auth" to="/login" onClick={closeMenu}>Sign in</Link>
                  <Link className="btn btn-gold btn-compact header-auth" to="/register" onClick={closeMenu}>Create account</Link>
                </>
              )}
              <button
                className={`nav-toggle${menuOpen ? " is-open" : ""}`}
                type="button"
                aria-expanded={menuOpen}
                aria-controls="site-nav"
                onClick={() => setMenuOpen((open) => !open)}
              >
                <span className="nav-toggle-bars" aria-hidden="true" />
                <span className="sr-only">{menuOpen ? "Close menu" : "Open menu"}</span>
              </button>
            </div>
          </div>
          <nav
            id="site-nav"
            className={`nav${menuOpen ? " is-open" : ""}`}
            aria-label="Auction rooms"
          >
            <NavLink to="/auctions/live" onClick={closeMenu}>Live</NavLink>
            <NavLink to="/auctions/upcoming" onClick={closeMenu}>Upcoming</NavLink>
            <NavLink to="/auctions" end onClick={closeMenu}>Lot catalogue</NavLink>
            <NavLink to="/collections" onClick={closeMenu}>Collection catalogue</NavLink>
            {user && (
              <>
                <NavLink to="/auctions/new" onClick={closeMenu}>List a lot</NavLink>
                <NavLink to="/my-bids" onClick={closeMenu}>My bids</NavLink>
                <NavLink to="/my-lots" onClick={closeMenu}>My lots</NavLink>
                <NavLink to="/my-collections" onClick={closeMenu}>My collections</NavLink>
                <NavLink to="/payments" onClick={closeMenu}>Payments</NavLink>
              </>
            )}
            {!user && (
              <>
                <NavLink className="nav-auth-link" to="/login" onClick={closeMenu}>Sign in</NavLink>
                <NavLink className="nav-auth-link" to="/register" onClick={closeMenu}>Create account</NavLink>
              </>
            )}
          </nav>
        </div>
      </header>
      <Outlet />
      <footer className="site-footer">
        <div className="wrap footer-inner">
          <p>Lotline keeps emails, card numbers, and other bidders’ history off the public floor.</p>
          <p>Demo cards: <code>4242424242424242</code> succeeds · <code>4000000000000002</code> declines</p>
        </div>
      </footer>
    </>
  );
}
