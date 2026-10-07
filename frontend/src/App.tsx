import {
  BrowserRouter,
  Routes,
  Route,
  useLocation,
} from "react-router-dom";
import {
  useEffect,
  useLayoutEffect,
  useRef,
} from "react";

import Navbar from "./components/Navbar";
import Footer from "./components/Footer";

import Home from "./pages/Home";
import Services from "./pages/Services";
import About from "./pages/About";
import Contact from "./pages/Contact";
import Intake from "./pages/Intake";
import Login from "./pages/Login";
import Signup from "./pages/Signup";
import AdminLogin from "./pages/AdminLogin";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import AdminForgotPassword from "./pages/AdminForgotPassword";
import AdminResetPassword from "./pages/AdminResetPassword";
import {
  Accessibility,
  Privacy,
  Sitemap,
} from "./pages/InformationPages";

import "./AccessibilityOverrides.css";

function RouteFocus() {
  const { pathname, search, hash, key } = useLocation();
  const announcement = useRef<HTMLParagraphElement>(null);

  /*
   * Prevent the browser from restoring the previous page's
   * scroll position automatically.
   */
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

  /*
   * Run whenever the user visits a different route,
   * including browser Back and Forward navigation.
   */
  useLayoutEffect(() => {
    const mainContent =
      document.getElementById("main-content");

    // Move screen-reader focus without changing scroll position.
    mainContent?.focus({
      preventScroll: true,
    });

    // Reset all possible page-scrolling elements.
    window.scrollTo({
      top: 0,
      left: 0,
      behavior: "auto",
    });

    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;

    /*
     * Run again after React finishes painting.
     * This prevents browsers from restoring the old position.
     */
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

  /*
   * Announce the page change to screen-reader users.
   */
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

  const isAdminLogin =
    location.pathname === "/admin-login";

  return (
    <>
      <a
        className="skip-link"
        href="#main-content"
      >
        Skip to main content
      </a>

      <RouteFocus />

      {!isAdminLogin && <Navbar />}

      <div
        id="main-content"
        tabIndex={-1}
      >
        <Routes>
          {/* Main website */}
          <Route
            path="/"
            element={<Home />}
          />

          <Route
            path="/about"
            element={<About />}
          />

          <Route
            path="/services"
            element={<Services />}
          />

          <Route
            path="/contact"
            element={<Contact />}
          />

          <Route
            path="/intake"
            element={<Intake />}
          />

          {/* Client account */}
          <Route
            path="/login"
            element={<Login />}
          />

          <Route
            path="/signup"
            element={<Signup />}
          />

          <Route
            path="/forgot-password"
            element={<ForgotPassword />}
          />

          <Route
            path="/reset-password"
            element={<ResetPassword />}
          />

          {/* Administrator account */}
          <Route
            path="/admin-login"
            element={<AdminLogin />}
          />

          <Route
            path="/admin/forgot-password"
            element={<AdminForgotPassword />}
          />

          <Route
            path="/admin/reset-password"
            element={<AdminResetPassword />}
          />

          {/* Information pages */}
          <Route
            path="/privacy"
            element={<Privacy />}
          />

          <Route
            path="/accessibility"
            element={<Accessibility />}
          />

          <Route
            path="/sitemap"
            element={<Sitemap />}
          />
        </Routes>
      </div>

      {!isAdminLogin && <Footer />}
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