import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import type { Role } from './api/types';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { MyClaimsPage } from './pages/MyClaimsPage';
import { NewClaimPage } from './pages/NewClaimPage';

const HOME: Record<Role, string> = { EMPLOYEE: '/claims', MANAGER: '/approvals', FINANCE: '/approvals', ADMIN: '/users' };

function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <div className="center-screen"><span className="spinner" aria-label="Loading" /></div>;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

/** Sends the signed-in user to the screen they will use most. */
function RoleHome() {
  const { user } = useAuth();
  return <Navigate to={HOME[user!.role]} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<RoleHome />} />
          <Route path="claims" element={<MyClaimsPage />} />
          <Route path="claims/new" element={<NewClaimPage />} />
          <Route path="*" element={<p className="muted">This screen is not built yet.</p>} />
        </Route>
      </Route>
    </Routes>
  );
}
