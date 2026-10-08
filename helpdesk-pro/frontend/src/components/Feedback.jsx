import Icon from './Icon.jsx';

export function Spinner({ label = 'Cargando…' }) {
  return (
    <div className="spinner-wrap" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorBanner({ error }) {
  if (!error) return null;
  return (
    <div className="alert alert-error" role="alert">
      <Icon name="alert" size={16} />
      <span>{error.message ?? String(error)}</span>
    </div>
  );
}

export function EmptyState({ title, children }) {
  return (
    <div className="empty">
      <Icon name="inbox" size={32} />
      <strong>{title}</strong>
      {children && <p>{children}</p>}
    </div>
  );
}
