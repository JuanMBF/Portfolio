export default function Pagination({ page, totalPages, totalElements, onChange }) {
  if (totalElements === 0) return null;
  return (
    <nav className="pagination" aria-label="Paginación">
      <span className="muted">
        {totalElements} {totalElements === 1 ? 'ticket' : 'tickets'} · página {page + 1} de {Math.max(totalPages, 1)}
      </span>
      <div className="pagination-buttons">
        <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Anterior
        </button>
        <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
          Siguiente
        </button>
      </div>
    </nav>
  );
}
