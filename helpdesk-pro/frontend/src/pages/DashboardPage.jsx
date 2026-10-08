import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { dashboardApi } from '../api/client.js';
import { BarList, ColumnChart } from '../components/Charts.jsx';
import { ErrorBanner, Spinner } from '../components/Feedback.jsx';
import Icon from '../components/Icon.jsx';
import { CATEGORY_LABELS, PRIORITY_LABELS, STATUS_LABELS } from '../utils/labels.js';

const dayFormat = new Intl.DateTimeFormat('es-AR', { weekday: 'short', day: 'numeric', timeZone: 'UTC' });
const longDayFormat = new Intl.DateTimeFormat('es-AR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });

function toBars(map, labels, { sort = false } = {}) {
  const bars = Object.entries(map).map(([key, value]) => ({ label: labels[key], value }));
  return sort ? bars.sort((a, b) => b.value - a.value) : bars;
}

export default function DashboardPage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    dashboardApi.get().then(setData).catch(setError);
  }, []);

  if (error) return <ErrorBanner error={error} />;
  if (!data) return <Spinner />;

  const trend = data.createdLast7Days.map((d) => {
    const date = new Date(`${d.date}T00:00:00Z`);
    return { label: dayFormat.format(date), tooltip: longDayFormat.format(date), value: d.count };
  });

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p className="muted">Estado de la mesa de ayuda en este momento.</p>
        </div>
        <Link to="/tickets?scope=unassigned" className="btn btn-ghost">
          Ver cola sin asignar
        </Link>
      </header>

      <section className="kpis">
        <Kpi label="Tickets activos" value={data.activeTickets} hint={`${data.totalTickets} en total`} />
        <Kpi label="Sin asignar" value={data.unassignedTickets} hint="Esperando un técnico" />
        <Kpi
          label="SLA vencidos"
          value={data.slaBreachedActive}
          hint="Activos fuera de término"
          status={data.slaBreachedActive > 0 ? 'critical' : 'good'}
        />
        <Kpi
          label="Cumplimiento de SLA"
          value={data.slaComplianceRate == null ? '—' : `${data.slaComplianceRate}%`}
          hint="Sobre tickets resueltos"
        />
        <Kpi
          label="Tiempo medio de resolución"
          value={data.averageResolutionHours == null ? '—' : `${data.averageResolutionHours} h`}
          hint="Desde la creación"
        />
      </section>

      <section className="charts">
        <div className="card chart-card chart-wide">
          <h2 className="card-title">Tickets creados en los últimos 7 días</h2>
          <ColumnChart data={trend} />
        </div>
        <div className="card chart-card">
          <h2 className="card-title">Tickets por estado</h2>
          <BarList data={toBars(data.byStatus, STATUS_LABELS)} />
        </div>
        <div className="card chart-card">
          <h2 className="card-title">Activos por prioridad</h2>
          <BarList data={toBars(data.activeByPriority, PRIORITY_LABELS).reverse()} emptyText="No hay tickets activos" />
        </div>
        <div className="card chart-card">
          <h2 className="card-title">Tickets por categoría</h2>
          <BarList data={toBars(data.byCategory, CATEGORY_LABELS, { sort: true })} />
        </div>
        <div className="card chart-card">
          <h2 className="card-title">Carga activa por técnico</h2>
          <BarList
            data={data.agentWorkload.map((a) => ({ label: a.agentName, value: a.activeTickets }))}
            emptyText="Ningún técnico tiene tickets asignados"
          />
        </div>
      </section>
    </>
  );
}

function Kpi({ label, value, hint, status }) {
  return (
    <div className={`card kpi ${status ? `kpi-${status}` : ''}`}>
      <span className="kpi-label">
        {status && <Icon name={status === 'critical' ? 'alert' : 'check'} size={14} />}
        {label}
      </span>
      <strong className="kpi-value">{value}</strong>
      <span className="muted small">{hint}</span>
    </div>
  );
}
