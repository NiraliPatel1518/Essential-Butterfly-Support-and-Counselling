import {
  BrowserRouter,
  Link,
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
import PublicFeatureRoute from "./components/PublicFeatureRoute";

import Home from "./pages/Home";
import Services from "./pages/Services";
import About from "./pages/About";
import Contact from "./pages/Contact";
import IntakeAccessGuard from "./components/IntakeAccessGuard";
import Login from "./pages/Login";
import Signup from "./pages/Signup";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import Updates from "./pages/Updates";
import Testimonials from "./pages/Testimonials";
import Resources from "./pages/Resources";
import Appointments from "./pages/Appointments";
import NewsletterUnsubscribe from "./pages/NewsletterUnsubscribe";

import AdminDashboard from "./pages/AdminDashboard";
import AdminAppointments from "./pages/AdminAppointments";
import MemoryWall from "./pages/MemoryWall";
import AdminMemoryWall from "./pages/AdminMemoryWall";
import ClientDashboard from "./pages/ClientDashboard";
import MyBookings from "./pages/MyBookings";

import {
  Accessibility,
  Privacy,
  Sitemap,
} from "./pages/InformationPages";

import { PublicFeatureProvider } from "./context/PublicFeatureContext";

import "./AccessibilityOverrides.css";

const pageTitles: Record<string, string> = {
  "/": "Home",
  "/about": "About Casey",
  "/services": "Services",
  "/contact": "Contact",
  "/testimonials": "Testimonials",
  "/resources": "Resource Library",
  "/appointments": "Available Appointments",
  "/memory-wall": "Memory Wall",
  "/updates": "Updates",
  "/newsletter/unsubscribe": "Newsletter Unsubscribe",
  "/intake": "Secure Intake",
  "/login": "Sign In",
  "/signup": "Create Account",
  "/forgot-password": "Forgot Password",
  "/reset-password": "Reset Password",
  "/admin-dashboard": "Admin Dashboard",
  "/admin-appointments": "Manage Appointments",
  "/admin-memory-wall": "Manage Memory Wall",
  "/client-dashboard": "Client Dashboard",
  "/my-bookings": "My Bookings",
  "/privacy": "Privacy",
  "/accessibility": "Accessibility",
  "/sitemap": "Site Map",
};

function getPageTitle(pathname: string) {
  if (pathname.startsWith("/updates/")) return "Update Details";
  return pageTitles[pathname] ?? "Page Not Found";
}

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
    const pageTitle = getPageTitle(pathname);
    document.title = `${pageTitle} | Essential Butterfly`;

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
      announcement.current.textContent = `Page changed to ${getPageTitle(pathname)}`;
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

  const isClientArea =
    location.pathname === "/client-dashboard" ||
    location.pathname === "/my-bookings" ||
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

          {/* Public testimonials */}
          <Route
            path="/testimonials"
            element={
              <PublicFeatureRoute feature="TESTIMONIALS">
                <Testimonials />
              </PublicFeatureRoute>
            }
          />

          {/* Public Resource Library (published resources only) */}
          <Route path="/resources" element={<Resources />} />

          {/* Public Memory Wall (published photos only) */}
          <Route
            path="/memory-wall"
            element={
              <PublicFeatureRoute feature="MEMORY_WALL">
                <MemoryWall />
              </PublicFeatureRoute>
            }
          />

          {/* Public appointment availability (open slots only) */}
          <Route
            path="/appointments"
            element={
              <PublicFeatureRoute feature="APPOINTMENTS">
                <Appointments />
              </PublicFeatureRoute>
            }
          />

          {/* Public website updates */}
          <Route
            path="/updates"
            element={
              <PublicFeatureRoute feature="UPDATES">
                <Updates />
              </PublicFeatureRoute>
            }
          />

          <Route
            path="/updates/:id"
            element={
              <PublicFeatureRoute feature="UPDATES">
                <Updates />
              </PublicFeatureRoute>
            }
          />

          {/* Public newsletter unsubscribe */}
          <Route
            path="/newsletter/unsubscribe"
            element={<NewsletterUnsubscribe />}
          />

          {/* Client-only intake with mandatory phone verification */}
          <Route
            path="/intake"
            element={
              <ProtectedRoute allowedRole="CLIENT">
                <IntakeAccessGuard />
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

          {/* Protected admin appointment management (admin portal only) */}
          <Route
            path="/admin-appointments"
            element={
              <ProtectedRoute allowedRole="ADMIN">
                <AdminAppointments />
              </ProtectedRoute>
            }
          />

          {/* Protected Memory Wall management (admin portal only) */}
          <Route
            path="/admin-memory-wall"
            element={
              <ProtectedRoute allowedRole="ADMIN">
                <AdminMemoryWall />
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

          {/* Client bookings: reached from the client dashboard */}
          <Route
            path="/my-bookings"
            element={
              <ProtectedRoute allowedRole="CLIENT">
                <MyBookings />
              </ProtectedRoute>
            }
          />

          {/* Information pages */}
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/accessibility" element={<Accessibility />} />
          <Route path="/sitemap" element={<Sitemap />} />

          <Route
            path="*"
            element={
              <main className="information-page">
                <h1>Page not found</h1>
                <p>The page you requested is unavailable or may have moved.</p>
                <Link to="/">Return to the home page</Link>
              </main>
            }
          />
        </Routes>
      </div>

      {!isClientArea && <Footer />}
    </>
  );
}

function App() {
  return (
    <BrowserRouter>
      <PublicFeatureProvider>
        <AppLayout />
      </PublicFeatureProvider>
    </BrowserRouter>
  );
}

export default App;
