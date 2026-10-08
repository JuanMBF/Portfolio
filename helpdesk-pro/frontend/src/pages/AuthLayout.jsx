import Icon from '../components/Icon.jsx';

const FEATURES = [
  ['clock', 'SLA por prioridad con alertas de vencimiento'],
  ['users', 'Roles de usuario, técnico y administrador'],
  ['dashboard', 'Métricas en tiempo real de la mesa de ayuda'],
  ['lock', 'Autenticación con JWT y permisos por endpoint'],
];

export default function AuthLayout({ children }) {
  return (
    <div className="auth">
      <section className="auth-hero">
        <div className="brand brand-light">
          <span className="brand-logo">
            <Icon name="headset" size={20} />
          </span>
          <span>
            HelpDesk <strong>Pro</strong>
          </span>
        </div>
        <h1>La mesa de ayuda que tu equipo de soporte necesita.</h1>
        <p>Registrá incidencias, priorizalas según su impacto y resolvelas dentro del SLA.</p>
        <ul className="auth-features">
          {FEATURES.map(([icon, text]) => (
            <li key={text}>
              <Icon name={icon} size={18} />
              {text}
            </li>
          ))}
        </ul>
      </section>
      <section className="auth-panel">{children}</section>
    </div>
  );
}
