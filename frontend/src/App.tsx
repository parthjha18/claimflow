import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import type { Role } from './api/types';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { MyClaimsPage } from './pages/MyClaimsPage';
import { NewClaimPage } from './pages/NewClaimPage';
import { ClaimDetailPage } from './pages/ClaimDetailPage';
import { ApprovalsPage } from './pages/ApprovalsPage';
import { PolicyPage } from './pages/PolicyPage';
import { UsersPage } from './pages/UsersPage';
import { ReportsPage } from './pages/ReportsPage';

const HOME: Record<Role, string> = { EMPLOYEE: '/claims', MANAGER: '/approvals', FINANCE: '/approvals', ADMIN: '/users' };

function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <div className="center-screen"><span className="spinner" aria-label="Loading" /></div>;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

/** Typing a URL for a screen your role cannot use sends you home instead of showing a wall of 403s. */
function RequireRole({ roles }: { roles: Role[] }) {
  const { user } = useAuth();
  return roles.includes(user!.role) ? <Outlet /> : <Navigate to="/" replace />;
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
          <Route path="policy" element={<PolicyPage />} />
          <Route element={<RequireRole roles={['MANAGER', 'FINANCE']} />}>
            <Route path="approvals" element={<ApprovalsPage />} />
          </Route>
          <Route element={<RequireRole roles={['ADMIN']} />}>
            <Route path="users" element={<UsersPage />} />
          </Route>
          <Route element={<RequireRole roles={['ADMIN', 'FINANCE']} />}>
            <Route path="reports" element={<ReportsPage />} />
          </Route>
          <Route path="claims" element={<MyClaimsPage />} />
          <Route path="claims/new" element={<NewClaimPage />} />
          <Route path="claims/:id" element={<ClaimDetailPage />} />
          <Route path="*" element={<p className="muted">This screen is not built yet.</p>} />
        </Route>
      </Route>
    </Routes>
  );
}
