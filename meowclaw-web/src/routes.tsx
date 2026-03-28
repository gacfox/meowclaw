import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { InitPage } from "@/pages/init";
import { LoginPage } from "@/pages/login";
import { NotFoundPage } from "@/pages/not-found";
import { Layout } from "@/components/layout";
import { useAuthStore } from "@/stores";

export function RoutesView() {
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();

  const requireAuth = (element: React.ReactElement) =>
    isAuthenticated ? (
      element
    ) : (
      <Navigate to="/login" state={{ from: location }} replace />
    );

  return (
    <Routes>
      <Route path="/init" element={<InitPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={requireAuth(<Layout />)} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
