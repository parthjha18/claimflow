import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';
import { ROLE_LABEL } from '../lib/format';
import { Icon, type IconName } from './Icon';

interface NavItem { to: string; label: string; icon: IconName; roles: Role[]; badge?: boolean }

const NAV: NavItem[] = [
  { to: '/approvals', label: 'Approvals', icon: 'inbox', roles: ['MANAGER', 'FINANCE'], badge: true },
  { to: '/claims', label: 'My claims', icon: 'claims', roles: ['EMPLOYEE', 'MANAGER', 'FINANCE'] },
  { to: '/claims/new', label: 'New claim', icon: 'plus', roles: ['EMPLOYEE', 'MANAGER', 'FINANCE'] },
  { to: '/users', label: 'Users', icon: 'users', roles: ['ADMIN'] },
  { to: '/reports', label: 'Reports', icon: 'chart', roles: ['ADMIN', 'FINANCE'] },
  { to: '/policy', label: 'Policy', icon: 'policy', roles: ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'] },
];

export function Layout() {
  const { user, logout } = useAuth();
  const role = user!.role;
  const items = NAV.filter((n) => n.roles.includes(role));

  const pending = useQuery({
    queryKey: ['pending-count'],
    queryFn: () => api.pendingApprovals({ size: 1 }),
    enabled: role === 'MANAGER' || role === 'FINANCE',
    refetchInterval: 30_000,
    select: (p) => p.totalElements,
  });

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark"><Icon name="check" size={18} stroke="#fff" strokeWidth={2.6} /></span>
          ClaimFlow
        </div>
        <nav className="nav" aria-label="Main">
          {items.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.to === '/claims'}>
              <Icon name={n.icon} />
              {n.label}
              {n.badge && !!pending.data && <span className="count">{pending.data}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="who">
            <strong>{user!.fullName}</strong>
            <span>{ROLE_LABEL[role]} · {user!.email}</span>
          </div>
          <button className="btn ghost sm" onClick={() => logout()}>
            <Icon name="logout" size={16} /> Sign out
          </button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
