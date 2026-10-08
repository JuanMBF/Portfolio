import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { ErrorBanner } from '../components/Feedback.jsx';
import AuthLayout from './AuthLayout.jsx';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const fieldError = (name) => error?.fieldErrors?.[name];
  const update = (name) => (e) => setForm({ ...form, [name]: e.target.value });

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await register(form);
      navigate('/tickets', { replace: true });
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <h2>Crear cuenta</h2>
        <p className="muted">Las cuentas nuevas se crean con rol de usuario.</p>

        <ErrorBanner error={error} />

        <label className="field">
          <span>Nombre y apellido</span>
          <input value={form.fullName} onChange={update('fullName')} autoComplete="name" />
          {fieldError('fullName') && <small className="field-error">{fieldError('fullName')}</small>}
        </label>
        <label className="field">
          <span>Email</span>
          <input type="email" value={form.email} onChange={update('email')} autoComplete="email" />
          {fieldError('email') && <small className="field-error">{fieldError('email')}</small>}
        </label>
        <label className="field">
          <span>Contraseña</span>
          <input type="password" value={form.password} onChange={update('password')} autoComplete="new-password" />
          {fieldError('password') && <small className="field-error">{fieldError('password')}</small>}
        </label>

        <button className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>

        <p className="muted center">
          ¿Ya tenés cuenta? <Link to="/login">Iniciá sesión</Link>
        </p>
      </form>
    </AuthLayout>
  );
}
