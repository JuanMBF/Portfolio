import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ticketsApi } from '../api/client.js';
import { ErrorBanner } from '../components/Feedback.jsx';
import Icon from '../components/Icon.jsx';
import { CATEGORY_LABELS, PRIORITY_LABELS, PRIORITY_SLA_HOURS } from '../utils/labels.js';

const PRIORITY_HELP = {
  LOW: 'Consultas o pedidos que pueden esperar.',
  MEDIUM: 'Algo no funciona pero hay alternativa.',
  HIGH: 'Afecta el trabajo de una persona o grupo.',
  CRITICAL: 'Interrumpe una clase, un evento o un servicio clave.',
};

const MAX_DESCRIPTION = 4000;

export default function NewTicketPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ title: '', description: '', category: 'SOFTWARE', priority: 'MEDIUM' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (name) => (e) => setForm({ ...form, [name]: e.target.value });
  const fieldError = (name) => error?.fieldErrors?.[name];

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const ticket = await ticketsApi.create(form);
      navigate(`/tickets/${ticket.id}`);
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  return (
    <>
      <header className="page-header">
        <div>
          <Link to="/tickets" className="back-link">
            <Icon name="back" size={16} /> Tickets
          </Link>
          <h1>Nuevo ticket</h1>
          <p className="muted">Contanos qué pasa y lo derivamos al equipo de soporte.</p>
        </div>
      </header>

      <form className="card form-card" onSubmit={handleSubmit} noValidate>
        <ErrorBanner error={error} />

        <label className="field">
          <span>Título</span>
          <input value={form.title} onChange={update('title')} maxLength={150} placeholder="Ej.: No puedo ingresar al campus virtual" />
          {fieldError('title') && <small className="field-error">{fieldError('title')}</small>}
        </label>

        <div className="field-row">
          <label className="field">
            <span>Categoría</span>
            <select value={form.category} onChange={update('category')}>
              {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Prioridad</span>
            <select value={form.priority} onChange={update('priority')}>
              {Object.entries(PRIORITY_LABELS).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
            <small className="muted">
              {PRIORITY_HELP[form.priority]} Tiempo de resolución: {PRIORITY_SLA_HOURS[form.priority]} h.
            </small>
          </label>
        </div>

        <label className="field">
          <span>Descripción</span>
          <textarea
            rows={7}
            value={form.description}
            onChange={update('description')}
            maxLength={MAX_DESCRIPTION}
            placeholder="¿Qué estabas haciendo? ¿Qué mensaje de error aparece? ¿Desde cuándo pasa?"
          />
          <small className="muted right">{form.description.length}/{MAX_DESCRIPTION}</small>
          {fieldError('description') && <small className="field-error">{fieldError('description')}</small>}
        </label>

        <div className="form-actions">
          <Link to="/tickets" className="btn btn-ghost">Cancelar</Link>
          <button className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Creando…' : 'Crear ticket'}
          </button>
        </div>
      </form>
    </>
  );
}
