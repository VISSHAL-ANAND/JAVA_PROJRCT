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

export default function App() {
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
            <button
              key={label}
              className={active === label ? "nav-item active" : "nav-item"}
              onClick={() => setActive(label)}
            >
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
            <div className="avatar">VA</div>
            <div><strong>Visshal Anand</strong><span>Student</span></div>
          </div>
        </header>

        <section className="hero-card">
          <div>
            <p className="eyebrow">CAMPUSOS CONTROL CENTER</p>
            <h2>Everything happening on campus, in one place.</h2>
            <p className="hero-copy">
              Report issues, track tickets, manage resources and stay informed
              about campus operations.
            </p>
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
              <strong>Frontend foundation ready</strong>
              <span>Live ticket data will be connected during backend integration.</span>
            </div>
          </article>

          <article className="panel">
            <div className="panel-heading">
              <div><p className="eyebrow">Campus operations</p><h3>System center</h3></div>
              <span className="badge neutral">Ready</span>
            </div>
            <p className="panel-copy">
              React UI foundation is ready for the real Java services.
            </p>
            <button className="primary-button">Open workspace</button>
          </article>
        </section>
      </main>
    </div>
  );
}
