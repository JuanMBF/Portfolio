import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { ErrorBanner } from '../components/Feedback.jsx';
import AuthLayout from './AuthLayout.jsx';

export const DEMO_ACCOUNTS = [
  { label: 'Administrador', email: 'admin@helpdesk.dev', password: 'Admin123!' },
  { label: 'Técnico', email: 'lucia@helpdesk.dev', password: 'Agent123!' },
  { label: 'Usuario', email: 'sofia@helpdesk.dev', password: 'User123!' },
];

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(credentials) {
    setSubmitting(true);
    setError(null);
    try {
      await login(credentials.email, credentials.password);
      navigate(location.state?.from ?? '/', { replace: true });
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    submit(form);
  }

  function loginWithDemo(account) {
    setForm({ email: account.email, password: account.password });
    submit(account);
  }

  return (
    <AuthLayout>
      <form className="auth-form" onSubmit={handleSubmit}>
        <h2>Iniciar sesión</h2>
        <p className="muted">Ingresá con tu cuenta o probá la demo con un clic.</p>

        <ErrorBanner error={error} />

        <label className="field">
          <span>Email</span>
          <input
            type="email"
            autoComplete="email"
            required
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
        </label>
        <label className="field">
          <span>Contraseña</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
        </label>

        <button className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Ingresando…' : 'Ingresar'}
        </button>

        <div className="demo">
          <span className="demo-title">Cuentas de demo</span>
          <div className="demo-buttons">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                type="button"
                key={account.email}
                className="btn btn-ghost btn-sm"
                disabled={submitting}
                onClick={() => loginWithDemo(account)}
              >
                {account.label}
              </button>
            ))}
          </div>
        </div>

        <p className="muted center">
          ¿No tenés cuenta? <Link to="/register">Registrate</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
