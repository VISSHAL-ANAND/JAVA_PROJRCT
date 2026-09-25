import { useState } from "react";

const navItems = [
  ["Overview", "⌂"],
  ["Tickets", "✓"],
  ["Resources", "▣"],
  ["Notifications", "◌"],
  ["Emergency", "!"]
];

const stats = [
  ["Open tickets", "12", "4 high priority"],
  ["In progress", "07", "3 assigned to you"],
  ["Resources", "24", "18 currently available"],
  ["Alerts", "02", "Requires attention"]
];

function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = await fetch("http://localhost:8080/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
      });

      const data = await response.json();

      if (!response.ok || !data.success) {
        throw new Error(data.message || "Login failed");
      }

      onLogin(data.user);
    } catch (err) {
      setError(err.message || "Unable to connect to CAMPUSOS server");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="brand login-brand">
          <div className="brand-mark">C</div>
          <div><strong>CAMPUSOS</strong><span>Campus Operations</span></div>
        </div>
        <p className="eyebrow">SECURE ACCESS</p>
        <h1>Welcome back.</h1>
        <p className="login-copy">Sign in to access your campus operations workspace.</p>

        <form onSubmit={submit} className="login-form">
          <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@campusos.com" required /></label>
          <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" required /></label>
          {error && <div className="error-message">{error}</div>}
          <button className="primary-button login-button" disabled={loading}>
            {loading ? "Signing in..." : "Sign in"}
          </button>
        </form>

        <div className="login-footer">CAMPUSOS • Campus Operations & Emergency Management</div>
      </div>
    </div>
  );
}

function Dashboard({ user, onLogout }) {
  const [active, setActive] = useState("Overview");

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">C</div>
          <div><strong>CAMPUSOS</strong><span>Campus Operations</span></div>
        </div>

        <nav className="nav-list">
          {navItems.map(([label, icon]) => (
            <button key={label} className={active === label ? "nav-item active" : "nav-item"} onClick={() => setActive(label)}>
              <span className="nav-icon">{icon}</span><span>{label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <span className="status-dot" /> Campus system online
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <p className="eyebrow">Campus Operations</p>
            <h1>{active}</h1>
          </div>
          <div className="profile">
            <div className="avatar">{user.name.split(" ").map(x => x[0]).slice(0, 2).join("")}</div>
            <div><strong>{user.name}</strong><span>{user.role}</span></div>
            <button className="text-button" onClick={onLogout}>Logout</button>
          </div>
        </header>

        <section className="hero-card">
          <div>
            <p className="eyebrow">CAMPUSOS CONTROL CENTER</p>
            <h2>Everything happening on campus, in one place.</h2>
            <p className="hero-copy">Report issues, track tickets, manage resources and stay informed about campus operations.</p>
          </div>
          <div className="hero-orb"><span /><span /><span /></div>
        </section>

        <section className="stats-grid">
          {stats.map(([label, value, detail]) => (
            <article className="stat-card" key={label}>
              <span>{label}</span><strong>{value}</strong><small>{detail}</small>
            </article>
          ))}
        </section>

        <section className="content-grid">
          <article className="panel">
            <div className="panel-heading">
              <div><p className="eyebrow">Recent activity</p><h3>Latest tickets</h3></div>
              <button className="text-button">View all</button>
            </div>
            <div className="empty-state">
              <div className="empty-icon">✓</div>
              <strong>Frontend connected</strong>
              <span>Authenticated as {user.email}. Live ticket data comes next.</span>
            </div>
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div><p className="eyebrow">Campus operations</p><h3>System center</h3></div>
              <span className="badge neutral">Connected</span>
            </div>
            <p className="panel-copy">React is now communicating with the Java HTTP API and MySQL authentication layer.</p>
            <button className="primary-button">Open workspace</button>
          </article>
        </section>
      </main>
    </div>
  );
}

export default function App() {
  const [user, setUser] = useState(null);

  return user
    ? <Dashboard user={user} onLogout={() => setUser(null)} />
    : <Login onLogin={setUser} />;
}
