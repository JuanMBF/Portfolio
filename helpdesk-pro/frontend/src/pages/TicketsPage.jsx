import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ticketsApi } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import Avatar from '../components/Avatar.jsx';
import { PriorityBadge, SlaBadge, StatusBadge } from '../components/Badges.jsx';
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback.jsx';
import Icon from '../components/Icon.jsx';
import Pagination from '../components/Pagination.jsx';
import { CATEGORY_LABELS, PRIORITY_LABELS, STATUS_LABELS, ticketCode } from '../utils/labels.js';
import { timeAgo } from '../utils/time.js';

const SCOPES = [
  { value: '', label: 'Todos' },
  { value: 'mine', label: 'Asignados a mí' },
  { value: 'unassigned', label: 'Sin asignar' },
];

/** Los filtros viven en la URL: se pueden compartir y sobreviven a un refresh. */
export default function TicketsPage() {
  const { isStaff } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') ?? '');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const scope = params.get('scope') ?? '';
  const urlQuery = params.get('q') ?? '';
  const queryString = params.toString();

  // Si la URL cambia desde afuera (por ejemplo, al tocar "Tickets" en el menú), el buscador se sincroniza.
  useEffect(() => {
    setSearch((current) => (current.trim() === urlQuery ? current : urlQuery));
  }, [urlQuery]);

  // Búsqueda con debounce para no disparar una request por cada tecla.
  useEffect(() => {
    const value = search.trim();
    if (value === urlQuery) return undefined;
    const timer = setTimeout(() => updateParam('q', value), 300);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  useEffect(() => {
    const current = new URLSearchParams(queryString);
    const query = { page: current.get('page') ?? 0, size: 10 };
    for (const key of ['q', 'status', 'priority', 'category']) {
      if (current.get(key)) query[key] = current.get(key);
    }
    const currentScope = current.get('scope');
    if (currentScope === 'mine') query.mine = true;
    if (currentScope === 'unassigned') query.unassigned = true;

    let cancelled = false;
    setError(null);
    ticketsApi
      .search(query)
      .then((data) => !cancelled && setResult(data))
      .catch((err) => !cancelled && setError(err));
    return () => {
      cancelled = true;
    };
  }, [queryString]);

  function updateParam(key, value) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value === '' || value == null) next.delete(key);
        else next.set(key, value);
        if (key !== 'page') next.delete('page');
        return next;
      },
      { replace: true },
    );
  }

  return (
    <>
      <header className="page-header">
        <div>
          <h1>{isStaff ? 'Tickets' : 'Mis tickets'}</h1>
          <p className="muted">
            {isStaff ? 'Todas las solicitudes de soporte de la organización.' : 'Seguí el estado de tus solicitudes.'}
          </p>
        </div>
        <Link to="/tickets/new" className="btn btn-primary">
          <Icon name="plus" size={16} /> Nuevo ticket
        </Link>
      </header>

      <section className="card filters">
        <label className="search">
          <Icon name="search" size={16} />
          <input
            placeholder="Buscar por título, descripción o #número"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Buscar tickets"
          />
        </label>
        <FilterSelect label="Estado" value={params.get('status')} options={STATUS_LABELS} onChange={(v) => updateParam('status', v)} />
        <FilterSelect label="Prioridad" value={params.get('priority')} options={PRIORITY_LABELS} onChange={(v) => updateParam('priority', v)} />
        <FilterSelect label="Categoría" value={params.get('category')} options={CATEGORY_LABELS} onChange={(v) => updateParam('category', v)} />
        {isStaff && (
          <div className="segmented" role="group" aria-label="Alcance">
            {SCOPES.map((option) => (
              <button
                key={option.value}
                className={scope === option.value ? 'active' : ''}
                onClick={() => updateParam('scope', option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>
        )}
      </section>

      <ErrorBanner error={error} />

      <section className="card table-card">
        {!result && !error && <Spinner />}
        {result && result.content.length === 0 && (
          <EmptyState title="No hay tickets para mostrar">Probá con otros filtros o creá un ticket nuevo.</EmptyState>
        )}
        {result && result.content.length > 0 && (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Ticket</th>
                  <th>Estado</th>
                  <th>Prioridad</th>
                  {isStaff && <th>Asignado</th>}
                  <th>SLA</th>
                  <th>Actualizado</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((ticket) => (
                  <tr key={ticket.id} onClick={() => navigate(`/tickets/${ticket.id}`)} className="row-link">
                    <td>
                      <Link to={`/tickets/${ticket.id}`} className="ticket-title" onClick={(e) => e.stopPropagation()}>
                        <span className="ticket-code">{ticketCode(ticket.id)}</span>
                        {ticket.title}
                      </Link>
                      <span className="muted small">
                        {CATEGORY_LABELS[ticket.category]} · {ticket.requester.fullName}
                      </span>
                    </td>
                    <td><StatusBadge status={ticket.status} /></td>
                    <td><PriorityBadge priority={ticket.priority} /></td>
                    {isStaff && (
                      <td>
                        {ticket.assignee ? (
                          <span className="person">
                            <Avatar user={ticket.assignee} size="sm" />
                            {ticket.assignee.fullName}
                          </span>
                        ) : (
                          <span className="muted">Sin asignar</span>
                        )}
                      </td>
                    )}
                    <td><SlaBadge ticket={ticket} /></td>
                    <td className="muted nowrap">{timeAgo(ticket.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {result && (
          <Pagination
            page={result.page}
            totalPages={result.totalPages}
            totalElements={result.totalElements}
            onChange={(page) => updateParam('page', page)}
          />
        )}
      </section>
    </>
  );
}

function FilterSelect({ label, value, options, onChange }) {
  return (
    <select value={value ?? ''} onChange={(e) => onChange(e.target.value)} aria-label={label}>
      <option value="">{label}: todos</option>
      {Object.entries(options).map(([key, text]) => (
        <option key={key} value={key}>
          {text}
        </option>
      ))}
    </select>
  );
}
