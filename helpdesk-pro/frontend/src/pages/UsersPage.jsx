import { useEffect, useState } from 'react';
import { usersApi } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import Avatar from '../components/Avatar.jsx';
import { ErrorBanner, Spinner } from '../components/Feedback.jsx';
import { ROLE_LABELS } from '../utils/labels.js';
import { formatDateTime } from '../utils/time.js';

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    usersApi.list().then(setUsers).catch(setError);
  }, []);

  async function changeRole(user, role) {
    setError(null);
    try {
      const updated = await usersApi.changeRole(user.id, role);
      setUsers((list) => list.map((u) => (u.id === updated.id ? updated : u)));
    } catch (err) {
      setError(err);
    }
  }

  return (
    <>
      <header className="page-header">
        <div>
          <h1>Usuarios</h1>
          <p className="muted">Gestioná los roles del equipo. Las cuentas nuevas se registran como usuarios.</p>
        </div>
      </header>

      <ErrorBanner error={error} />

      <section className="card table-card">
        {!users ? (
          <Spinner />
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Email</th>
                  <th>Alta</th>
                  <th>Rol</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <span className="person">
                        <Avatar user={user} size="sm" /> {user.fullName}
                      </span>
                    </td>
                    <td className="muted">{user.email}</td>
                    <td className="muted nowrap">{formatDateTime(user.createdAt)}</td>
                    <td>
                      <select
                        value={user.role}
                        disabled={user.id === currentUser.id}
                        title={user.id === currentUser.id ? 'No podés cambiar tu propio rol' : undefined}
                        onChange={(e) => changeRole(user, e.target.value)}
                        aria-label={`Rol de ${user.fullName}`}
                      >
                        {Object.entries(ROLE_LABELS).map(([key, label]) => (
                          <option key={key} value={key}>{label}</option>
                        ))}
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
