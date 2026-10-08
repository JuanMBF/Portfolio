const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

const dateTimeFormat = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' });

export function formatDateTime(iso) {
  return iso ? dateTimeFormat.format(new Date(iso)) : '—';
}

/** Duración legible: "45 min", "3 h 20 min", "2 d 4 h". Redondea primero para evitar cosas como "23 h 60 min". */
export function formatDuration(ms) {
  const totalMinutes = Math.max(1, Math.round(Math.abs(ms) / MINUTE));
  if (totalMinutes < 60) return `${totalMinutes} min`;
  if (totalMinutes < 24 * 60) {
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return minutes ? `${hours} h ${minutes} min` : `${hours} h`;
  }
  const totalHours = Math.round(totalMinutes / 60);
  const days = Math.floor(totalHours / 24);
  const hours = totalHours % 24;
  return hours ? `${days} d ${hours} h` : `${days} d`;
}

export function timeAgo(iso, now = Date.now()) {
  const diff = now - new Date(iso).getTime();
  if (diff < MINUTE) return 'recién';
  return `hace ${formatDuration(diff)}`;
}

/**
 * Estado del SLA para mostrar en la UI:
 *  - breached: vencido (activo y pasado de fecha, o resuelto tarde)
 *  - at-risk:  activo y le queda menos del 25 % del tiempo
 *  - ok:       activo y en tiempo
 *  - met:      resuelto dentro del SLA
 *  - none:     cancelado sin resolución
 */
export function slaStatus(ticket, now = Date.now()) {
  const due = new Date(ticket.slaDueAt).getTime();
  const created = new Date(ticket.createdAt).getTime();
  const active = ['OPEN', 'IN_PROGRESS', 'WAITING_ON_USER'].includes(ticket.status);

  if (!active) {
    if (!ticket.resolvedAt) return { state: 'none', label: 'Sin SLA' };
    return ticket.slaBreached
      ? { state: 'breached', label: 'SLA incumplido' }
      : { state: 'met', label: 'SLA cumplido' };
  }

  const remaining = due - now;
  if (remaining < 0) return { state: 'breached', label: `Vencido hace ${formatDuration(remaining)}` };
  const state = remaining < (due - created) * 0.25 ? 'at-risk' : 'ok';
  return { state, label: `Vence en ${formatDuration(remaining)}` };
}
