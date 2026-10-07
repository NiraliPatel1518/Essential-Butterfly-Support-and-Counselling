import {
  BrowserRouter,
  Routes,
  Route,
  useLocation,
} from "react-router-dom";
import { useEffect, useRef } from "react";

import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import SessionTimeout from "./components/SessionTimeout";

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
import { Accessibility, Privacy, Sitemap } from "./pages/InformationPages";
import "./AccessibilityOverrides.css";

function RouteFocus() {
  const { pathname } = useLocation();
  const announcement = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "auto" });
    document.getElementById("main-content")?.focus();
    if (announcement.current) {
      announcement.current.textContent = `Page changed to ${document.title}`;
    }
  }, [pathname]);

  return <p ref={announcement} className="sr-only" aria-live="polite" />;
}

function AppLayout() {
  const location = useLocation();

  const isAdminLogin = location.pathname === "/admin-login";

  return (
    <>
      <SessionTimeout />

      <a className="skip-link" href="#main-content">Skip to main content</a>
      <RouteFocus />
      {!isAdminLogin && <Navbar />}

      <div id="main-content" tabIndex={-1}>
        <Routes>
        {/* Main Website */}
        <Route path="/" element={<Home />} />
        <Route path="/about" element={<About />} />
        <Route path="/services" element={<Services />} />
        <Route path="/contact" element={<Contact />} />
        <Route path="/intake" element={<Intake />} />

        {/* Client Account */}
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route
          path="/forgot-password"
          element={<ForgotPassword />}
        />
        <Route
          path="/reset-password"
          element={<ResetPassword />}
        />

        {/* Admin Account */}
        <Route path="/admin-login" element={<AdminLogin />} />
        <Route
          path="/admin/forgot-password"
          element={<AdminForgotPassword />}
        />
        <Route
          path="/admin/reset-password"
          element={<AdminResetPassword />}
        />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/accessibility" element={<Accessibility />} />
          <Route path="/sitemap" element={<Sitemap />} />
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