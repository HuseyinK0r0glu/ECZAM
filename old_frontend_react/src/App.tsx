import { Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "./routes/ProtectedRoute";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import Inventory from "./pages/Inventory";
import MedicationDetail from "./pages/MedicationDetail";
import Schedules from "./pages/Schedules";
import Logs from "./pages/Logs";
import Expiration from "./pages/Expiration";
import AiAssistant from "./pages/AiAssistant";
import Offline from "./pages/Offline";
import LandingPage from "./pages/LandingPage";
import AddMedicationForm from "./features/medications/AddMedicationForm";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/offline" element={<Offline />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/inventory" element={<Inventory />} />
        <Route path="/inventory/add" element={<AddMedicationForm />} />
        <Route path="/medications/:id" element={<MedicationDetail />} />
        <Route path="/schedules" element={<Schedules />} />
        <Route path="/logs" element={<Logs />} />
        <Route path="/expiration" element={<Expiration />} />
        <Route path="/assistant" element={<AiAssistant />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
