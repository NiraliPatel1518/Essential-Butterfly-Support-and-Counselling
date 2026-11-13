import { useState } from "react";
import AdminContentPages from "./AdminContentPages";
import AdminResources from "./AdminResources";
import AdminContentActivity from "./AdminContentActivity";
import "./AdminContent.css";

type Tab = "pages" | "resources" | "activity";

const TABS: { key: Tab; label: string }[] = [
  { key: "pages", label: "Website pages" },
  { key: "resources", label: "Resource Library" },
  { key: "activity", label: "Recent changes" },
];

/* UC-09: Manage Website Content and Resources (administrator dashboard). */
function AdminContent() {
  const [tab, setTab] = useState<Tab>("pages");

  return (
    <section className="admin-content" aria-labelledby="admin-content-title">
      <header className="admin-content-heading">
        <p className="dashboard-eyebrow">PUBLIC WEBSITE CONTENT</p>
        <h2 id="admin-content-title">Manage Website Content</h2>
        <p>
          Edit the Service, About and Privacy pages, and manage the public
          Resource Library.
        </p>
      </header>

      <nav className="admin-content-tabs" aria-label="Website content sections">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            className={tab === item.key ? "active" : ""}
            aria-current={tab === item.key ? "page" : undefined}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {tab === "pages" && <AdminContentPages />}
      {tab === "resources" && <AdminResources />}
      {tab === "activity" && <AdminContentActivity />}
    </section>
  );
}

export default AdminContent;
