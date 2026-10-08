import { PRIORITY_LABELS, STATUS_LABELS } from '../utils/labels.js';
import { slaStatus } from '../utils/time.js';
import Icon from './Icon.jsx';

export function StatusBadge({ status }) {
  return <span className={`badge status-${status}`}>{STATUS_LABELS[status]}</span>;
}

export function PriorityBadge({ priority }) {
  return (
    <span className={`priority priority-${priority}`}>
      <span className="priority-dot" aria-hidden="true" />
      {PRIORITY_LABELS[priority]}
    </span>
  );
}

const SLA_ICONS = { breached: 'alert', 'at-risk': 'clock', ok: 'clock', met: 'check', none: 'clock' };

/** El estado del SLA siempre va con ícono + texto: nunca se comunica solo con color. */
export function SlaBadge({ ticket, now }) {
  const { state, label } = slaStatus(ticket, now);
  return (
    <span className={`sla sla-${state}`} title={`Vencimiento del SLA: ${new Date(ticket.slaDueAt).toLocaleString('es-AR')}`}>
      <Icon name={SLA_ICONS[state]} size={14} />
      {label}
    </span>
  );
}
