import { render, screen } from '@testing-library/react';
import { PriorityBadge, SlaBadge, StatusBadge } from './Badges.jsx';

const HOUR = 3_600_000;
const NOW = new Date('2026-03-02T12:00:00Z').getTime();

describe('Badges', () => {
  it('muestra el estado en español', () => {
    render(<StatusBadge status="WAITING_ON_USER" />);
    expect(screen.getByText('Esperando usuario')).toHaveClass('status-WAITING_ON_USER');
  });

  it('muestra la prioridad con texto, no solo con color', () => {
    render(<PriorityBadge priority="CRITICAL" />);
    expect(screen.getByText('Crítica')).toBeInTheDocument();
  });

  it('marca el SLA vencido', () => {
    const ticket = {
      status: 'OPEN',
      createdAt: new Date(NOW - 10 * HOUR).toISOString(),
      slaDueAt: new Date(NOW - 2 * HOUR).toISOString(),
      resolvedAt: null,
      slaBreached: true,
    };
    render(<SlaBadge ticket={ticket} now={NOW} />);
    expect(screen.getByText('Vencido hace 2 h')).toHaveClass('sla-breached');
  });
});
