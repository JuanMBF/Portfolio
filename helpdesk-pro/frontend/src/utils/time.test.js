import { formatDuration, slaStatus, timeAgo } from './time.js';
import { humanizeCodes, ticketCode, transitionLabel } from './labels.js';

const HOUR = 3_600_000;
const NOW = new Date('2026-03-02T12:00:00Z').getTime();

function ticket(overrides) {
  return {
    status: 'OPEN',
    createdAt: new Date(NOW - 2 * HOUR).toISOString(),
    slaDueAt: new Date(NOW + 6 * HOUR).toISOString(),
    resolvedAt: null,
    slaBreached: false,
    ...overrides,
  };
}

describe('formatDuration', () => {
  it.each([
    [30 * 60_000, '30 min'],
    [3 * HOUR, '3 h'],
    [3 * HOUR + 20 * 60_000, '3 h 20 min'],
    [50 * HOUR, '2 d 2 h'],
    [-2 * HOUR, '2 h'],
    [24 * HOUR - 100, '1 d'],
    [HOUR - 100, '1 h'],
  ])('%i ms → %s', (ms, expected) => {
    expect(formatDuration(ms)).toBe(expected);
  });
});

describe('timeAgo', () => {
  it('muestra "recién" para menos de un minuto', () => {
    expect(timeAgo(new Date(NOW - 10_000).toISOString(), NOW)).toBe('recién');
  });

  it('muestra horas transcurridas', () => {
    expect(timeAgo(new Date(NOW - 5 * HOUR).toISOString(), NOW)).toBe('hace 5 h');
  });
});

describe('slaStatus', () => {
  it('ticket activo con tiempo de sobra está ok', () => {
    expect(slaStatus(ticket(), NOW)).toEqual({ state: 'ok', label: 'Vence en 6 h' });
  });

  it('ticket activo con menos del 25 % del tiempo está en riesgo', () => {
    const t = ticket({ slaDueAt: new Date(NOW + HOUR).toISOString(), createdAt: new Date(NOW - 7 * HOUR).toISOString() });
    expect(slaStatus(t, NOW).state).toBe('at-risk');
  });

  it('ticket activo pasado de fecha está vencido', () => {
    const t = ticket({ slaDueAt: new Date(NOW - 3 * HOUR).toISOString() });
    expect(slaStatus(t, NOW)).toEqual({ state: 'breached', label: 'Vencido hace 3 h' });
  });

  it('ticket resuelto a tiempo cumple el SLA', () => {
    const t = ticket({ status: 'RESOLVED', resolvedAt: new Date(NOW).toISOString() });
    expect(slaStatus(t, NOW).state).toBe('met');
  });

  it('ticket resuelto tarde incumple el SLA', () => {
    const t = ticket({ status: 'CLOSED', resolvedAt: new Date(NOW).toISOString(), slaBreached: true });
    expect(slaStatus(t, NOW).state).toBe('breached');
  });

  it('ticket cancelado no tiene SLA', () => {
    expect(slaStatus(ticket({ status: 'CLOSED' }), NOW).state).toBe('none');
  });
});

describe('labels', () => {
  it('formatea el código del ticket', () => {
    expect(ticketCode(7)).toBe('#0007');
  });

  it('traduce los códigos dentro de un texto', () => {
    expect(humanizeCodes('OPEN → IN_PROGRESS')).toBe('Abierto → En progreso');
  });

  it('distingue reabrir de iniciar', () => {
    expect(transitionLabel('OPEN', 'IN_PROGRESS')).toBe('Iniciar atención');
    expect(transitionLabel('RESOLVED', 'IN_PROGRESS')).toBe('Reabrir');
  });
});
