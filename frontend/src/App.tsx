import {
  BrowserRouter,
  Routes,
  Route,
  useLocation,
} from "react-router-dom";

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

function AppLayout() {
  const location = useLocation();

  const isAdminLogin = location.pathname === "/admin-login";

  return (
    <>
      {!isAdminLogin && <Navbar />}

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

        {/* Admin Account */}
        <Route path="/admin-login" element={<AdminLogin />} />

      </Routes>

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