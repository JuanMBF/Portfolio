/**
 * Gráficos livianos hechos con HTML + CSS (sin librerías). Una sola serie por gráfico,
 * así que usan un único color; el detalle de cada barra aparece al pasar el mouse
 * y también está disponible como tabla para lectores de pantalla.
 */

export function BarList({ data, emptyText = 'Sin datos' }) {
  const max = Math.max(...data.map((d) => d.value), 0);
  if (max === 0) return <p className="muted">{emptyText}</p>;

  return (
    <>
      <ul className="barlist" aria-hidden="true">
        {data.map((d) => (
          <li key={d.label} className="barlist-row" data-tip={`${d.label}: ${d.value}`}>
            <span className="barlist-label">{d.label}</span>
            <span className="barlist-track">
              <span className="barlist-bar" style={{ width: `${(d.value / max) * 100}%` }} />
            </span>
            <span className="barlist-value">{d.value}</span>
          </li>
        ))}
      </ul>
      <SrTable data={data} />
    </>
  );
}

export function ColumnChart({ data }) {
  const max = Math.max(...data.map((d) => d.value), 1);
  const peak = data.reduce((best, d) => (d.value > best.value ? d : best), data[0]);

  return (
    <>
      <div className="columns" aria-hidden="true">
        {data.map((d) => (
          <div key={d.label} className="column" data-tip={`${d.tooltip ?? d.label}: ${d.value}`}>
            <span className="column-value">{d === peak && d.value > 0 ? d.value : ''}</span>
            <span className="column-track">
              <span className="column-bar" style={{ height: `${(d.value / max) * 100}%` }} />
            </span>
            <span className="column-label">{d.label}</span>
          </div>
        ))}
      </div>
      <SrTable data={data} />
    </>
  );
}

function SrTable({ data }) {
  return (
    <table className="sr-only">
      <tbody>
        {data.map((d) => (
          <tr key={d.label}>
            <th scope="row">{d.tooltip ?? d.label}</th>
            <td>{d.value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
