import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  useLocation,
} from "react-router-dom";

import {
  useEffect,
  useLayoutEffect,
  useRef,
} from "react";

import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import ProtectedRoute from "./components/ProtectedRoute";

import Home from "./pages/Home";
import Services from "./pages/Services";
import About from "./pages/About";
import Contact from "./pages/Contact";
import Intake from "./pages/Intake";
import Login from "./pages/Login";
import Signup from "./pages/Signup";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";

import AdminDashboard from "./pages/AdminDashboard";
import ClientDashboard from "./pages/ClientDashboard";

import {
  Accessibility,
  Privacy,
  Sitemap,
} from "./pages/InformationPages";

import "./AccessibilityOverrides.css";

function RouteFocus() {
  const { pathname, search, hash, key } = useLocation();
  const announcement = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if ("scrollRestoration" in window.history) {
      window.history.scrollRestoration = "manual";
    }

    return () => {
      if ("scrollRestoration" in window.history) {
        window.history.scrollRestoration = "auto";
      }
    };
  }, []);

  useLayoutEffect(() => {
    const mainContent = document.getElementById("main-content");

    mainContent?.focus({ preventScroll: true });

    window.scrollTo({
      top: 0,
      left: 0,
      behavior: "auto",
    });

    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;

    const frame = window.requestAnimationFrame(() => {
      window.scrollTo({
        top: 0,
        left: 0,
        behavior: "auto",
      });

      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
    });

    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [pathname, search, hash, key]);

  useEffect(() => {
    if (announcement.current) {
      announcement.current.textContent =
        `Page changed to ${document.title}`;
    }
  }, [pathname, search, hash, key]);

  return (
    <p
      ref={announcement}
      className="sr-only"
      aria-live="polite"
      aria-atomic="true"
    />
  );
}

function AppLayout() {
  const location = useLocation();

  // Client portal pages use their own visual shell, not the public site header/footer.
  const isClientArea =
    location.pathname === "/client-dashboard" ||
    location.pathname === "/intake";

  return (
    <>
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <RouteFocus />

      {!isClientArea && <Navbar />}

      <div id="main-content" tabIndex={-1}>
        <Routes>
          {/* Main website */}
          <Route path="/" element={<Home />} />
          <Route path="/about" element={<About />} />
          <Route path="/services" element={<Services />} />
          <Route path="/contact" element={<Contact />} />

          {/* Client-only intake page */}
          <Route
            path="/intake"
            element={
              <ProtectedRoute allowedRole="CLIENT">
                <Intake />
              </ProtectedRoute>
            }
          />

          {/* Unified login */}
          <Route path="/login" element={<Login />} />

          <Route
            path="/admin-login"
            element={<Navigate to="/login" replace />}
          />

          {/* Client account pages */}
          <Route path="/signup" element={<Signup />} />

          <Route
            path="/forgot-password"
            element={<ForgotPassword />}
          />

          <Route
            path="/reset-password"
            element={<ResetPassword />}
          />

          {/* Legacy admin recovery URLs */}
          <Route
            path="/admin/forgot-password"
            element={<Navigate to="/forgot-password" replace />}
          />

          <Route
            path="/admin/reset-password"
            element={
              <Navigate
                to={`/reset-password${location.search}`}
                replace
              />
            }
          />

          {/* Protected admin dashboard */}
          <Route
            path="/admin-dashboard"
            element={
              <ProtectedRoute allowedRole="ADMIN">
                <AdminDashboard />
              </ProtectedRoute>
            }
          />

          {/* Protected client dashboard */}
          <Route
            path="/client-dashboard"
            element={
              <ProtectedRoute allowedRole="CLIENT">
                <ClientDashboard />
              </ProtectedRoute>
            }
          />

          {/* Information pages */}
          <Route path="/privacy" element={<Privacy />} />

          <Route
            path="/accessibility"
            element={<Accessibility />}
          />

          <Route path="/sitemap" element={<Sitemap />} />
        </Routes>
      </div>

      {!isClientArea && <Footer />}
    </>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AppLayout />
    </BrowserRouter>
  );
}

export default App;