import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ticketsApi, usersApi } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import Avatar from '../components/Avatar.jsx';
import { PriorityBadge, SlaBadge, StatusBadge } from '../components/Badges.jsx';
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback.jsx';
import Icon from '../components/Icon.jsx';
import Markdown from '../components/Markdown.jsx';
import {
  CATEGORY_LABELS,
  EVENT_LABELS,
  PRIORITY_LABELS,
  ROLE_LABELS,
  humanizeCodes,
  ticketCode,
  transitionLabel,
} from '../utils/labels.js';
import { formatDateTime, timeAgo } from '../utils/time.js';

export default function TicketDetailPage() {
  const { id } = useParams();
  const { user, isStaff } = useAuth();
  const [ticket, setTicket] = useState(null);
  const [comments, setComments] = useState([]);
  const [events, setEvents] = useState([]);
  const [agents, setAgents] = useState([]);
  const [error, setError] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [t, c, e] = await Promise.all([ticketsApi.get(id), ticketsApi.comments(id), ticketsApi.events(id)]);
      setTicket(t);
      setComments(c);
      setEvents(e);
      setError(null);
    } catch (err) {
      setError(err);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (isStaff) usersApi.agents().then(setAgents).catch(() => setAgents([]));
  }, [isStaff]);

  /** Ejecuta una acción sobre el ticket y recarga todo. Devuelve true si salió bien. */
  async function run(action) {
    setBusy(true);
    setActionError(null);
    try {
      await action();
      await load();
      return true;
    } catch (err) {
      setActionError(err);
      return false;
    } finally {
      setBusy(false);
    }
  }

  if (error?.status === 404) {
    return (
      <EmptyState title="Ticket no encontrado">
        <Link to="/tickets">Volver al listado</Link>
      </EmptyState>
    );
  }
  if (error) return <ErrorBanner error={error} />;
  if (!ticket) return <Spinner />;

  const isClosed = ticket.status === 'CLOSED';

  return (
    <>
      <header className="page-header">
        <div>
          <Link to="/tickets" className="back-link">
            <Icon name="back" size={16} /> Tickets
          </Link>
          <h1>
            <span className="ticket-code">{ticketCode(ticket.id)}</span> {ticket.title}
          </h1>
          <div className="header-badges">
            <StatusBadge status={ticket.status} />
            <PriorityBadge priority={ticket.priority} />
            <SlaBadge ticket={ticket} />
          </div>
        </div>
      </header>

      <ErrorBanner error={actionError} />

      <div className="detail-grid">
        <div className="detail-main">
          <section className="card">
            <div className="comment-head">
              <Avatar user={ticket.requester} />
              <div>
                <strong>{ticket.requester.fullName}</strong>
                <span className="muted small"> abrió este ticket {timeAgo(ticket.createdAt)}</span>
              </div>
            </div>
            <div className="description">
              <Markdown text={ticket.description} />
            </div>
          </section>

          <section className="card">
            <h2 className="card-title">Conversación</h2>
            {comments.length === 0 && <p className="muted">Todavía no hay comentarios.</p>}
            <ol className="comments">
              {comments.map((comment) => (
                <li key={comment.id} className={`comment ${comment.internal ? 'comment-internal' : ''}`}>
                  <div className="comment-head">
                    <Avatar user={comment.author} size="sm" />
                    <strong>{comment.author.fullName}</strong>
                    <span className="muted small">
                      {ROLE_LABELS[comment.author.role]} · {timeAgo(comment.createdAt)}
                    </span>
                    {comment.internal && (
                      <span className="internal-tag">
                        <Icon name="lock" size={12} /> Nota interna
                      </span>
                    )}
                  </div>
                  <p>{comment.body}</p>
                </li>
              ))}
            </ol>
            {isClosed ? (
              <p className="muted">El ticket está cerrado y no admite nuevos comentarios.</p>
            ) : (
              <CommentForm
                isStaff={isStaff}
                busy={busy}
                waitingOnMe={ticket.status === 'WAITING_ON_USER' && ticket.requester.id === user.id}
                onSubmit={(comment) => run(() => ticketsApi.addComment(ticket.id, comment))}
              />
            )}
          </section>
        </div>

        <aside className="detail-side">
          {isStaff && ticket.allowedTransitions.length > 0 && (
            <section className="card">
              <h2 className="card-title">Acciones</h2>
              <div className="actions">
                {ticket.allowedTransitions.map((next) => (
                  <button
                    key={next}
                    className={`btn btn-block ${next === 'RESOLVED' ? 'btn-primary' : 'btn-ghost'}`}
                    disabled={busy}
                    onClick={() => run(() => ticketsApi.changeStatus(ticket.id, next))}
                  >
                    {transitionLabel(ticket.status, next)}
                  </button>
                ))}
              </div>
            </section>
          )}

          <section className="card">
            <h2 className="card-title">Detalles</h2>
            <dl className="details">
              <dt>Solicitante</dt>
              <dd className="person">
                <Avatar user={ticket.requester} size="sm" /> {ticket.requester.fullName}
              </dd>

              <dt>Asignado a</dt>
              <dd>
                {isStaff && !isClosed ? (
                  <div className="inline-control">
                    <select
                      value={ticket.assignee?.id ?? ''}
                      disabled={busy}
                      aria-label="Asignar a"
                      onChange={(e) =>
                        run(() => ticketsApi.assign(ticket.id, e.target.value ? Number(e.target.value) : null))
                      }
                    >
                      <option value="">Sin asignar</option>
                      {agents.map((agent) => (
                        <option key={agent.id} value={agent.id}>
                          {agent.fullName}
                        </option>
                      ))}
                    </select>
                    {ticket.assignee?.id !== user.id && (
                      <button
                        className="btn btn-ghost btn-sm"
                        disabled={busy}
                        onClick={() => run(() => ticketsApi.assign(ticket.id, user.id))}
                      >
                        Asignarme
                      </button>
                    )}
                  </div>
                ) : ticket.assignee ? (
                  <span className="person">
                    <Avatar user={ticket.assignee} size="sm" /> {ticket.assignee.fullName}
                  </span>
                ) : (
                  <span className="muted">Sin asignar</span>
                )}
              </dd>

              <dt>Prioridad</dt>
              <dd>
                {isStaff && !['RESOLVED', 'CLOSED'].includes(ticket.status) ? (
                  <select
                    value={ticket.priority}
                    disabled={busy}
                    aria-label="Prioridad"
                    onChange={(e) => run(() => ticketsApi.changePriority(ticket.id, e.target.value))}
                  >
                    {Object.entries(PRIORITY_LABELS).map(([key, label]) => (
                      <option key={key} value={key}>{label}</option>
                    ))}
                  </select>
                ) : (
                  <PriorityBadge priority={ticket.priority} />
                )}
              </dd>

              <dt>Categoría</dt>
              <dd>{CATEGORY_LABELS[ticket.category]}</dd>

              <dt>Creado</dt>
              <dd>{formatDateTime(ticket.createdAt)}</dd>

              <dt>Vencimiento SLA</dt>
              <dd>{formatDateTime(ticket.slaDueAt)}</dd>

              {ticket.resolvedAt && (
                <>
                  <dt>Resuelto</dt>
                  <dd>{formatDateTime(ticket.resolvedAt)}</dd>
                </>
              )}
            </dl>
          </section>

          <section className="card">
            <h2 className="card-title">Historial</h2>
            <ol className="timeline">
              {events.map((event) => (
                <li key={event.id}>
                  <span className={`timeline-dot event-${event.type}`} aria-hidden="true" />
                  <div>
                    <strong>{EVENT_LABELS[event.type]}</strong>
                    <p>{humanizeCodes(event.detail)}</p>
                    <span className="muted small">
                      {event.actor.fullName} · {formatDateTime(event.createdAt)}
                    </span>
                  </div>
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>
    </>
  );
}

function CommentForm({ isStaff, busy, waitingOnMe, onSubmit }) {
  const [body, setBody] = useState('');
  const [internal, setInternal] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!body.trim()) return;
    const saved = await onSubmit({ body, internal });
    if (saved) {
      setBody('');
      setInternal(false);
    }
  }

  return (
    <form className={`comment-form ${internal ? 'is-internal' : ''}`} onSubmit={handleSubmit}>
      {waitingOnMe && (
        <div className="alert alert-info">
          El equipo de soporte está esperando tu respuesta. Al comentar, el ticket vuelve a la cola de atención.
        </div>
      )}
      <textarea
        rows={3}
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder={internal ? 'Nota visible solo para el equipo de soporte…' : 'Escribí una respuesta…'}
        maxLength={2000}
        aria-label="Comentario"
      />
      <div className="comment-form-actions">
        {isStaff ? (
          <label className="checkbox">
            <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
            Nota interna
          </label>
        ) : (
          <span />
        )}
        <button className="btn btn-primary btn-sm" disabled={busy || !body.trim()}>
          {internal ? 'Guardar nota' : 'Comentar'}
        </button>
      </div>
    </form>
  );
}
