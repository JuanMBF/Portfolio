import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { ROLE_LABELS } from '../utils/labels.js';
import Avatar from './Avatar.jsx';
import Icon from './Icon.jsx';

export default function Layout() {
  const { user, isStaff, isAdmin, logout } = useAuth();

  const links = [
    isStaff && { to: '/dashboard', icon: 'dashboard', label: 'Dashboard' },
    { to: '/tickets', icon: 'ticket', label: isStaff ? 'Tickets' : 'Mis tickets', end: true },
    { to: '/tickets/new', icon: 'plus', label: 'Nuevo ticket' },
    isAdmin && { to: '/users', icon: 'users', label: 'Usuarios' },
  ].filter(Boolean);

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-logo">
            <Icon name="headset" size={20} />
          </span>
          <span>
            HelpDesk <strong>Pro</strong>
          </span>
        </div>

        <nav className="nav">
          {links.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end} className="nav-link">
              <Icon name={link.icon} />
              <span>{link.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-user">
          <Avatar user={user} />
          <div className="sidebar-user-info">
            <strong>{user.fullName}</strong>
            <span>{ROLE_LABELS[user.role]}</span>
          </div>
          <button className="icon-btn" onClick={logout} title="Cerrar sesión" aria-label="Cerrar sesión">
            <Icon name="logout" />
          </button>
        </div>
      </aside>

      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
