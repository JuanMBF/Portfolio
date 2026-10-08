import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext.jsx';
import LoginPage from './LoginPage.jsx';

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Inicio</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function jsonResponse(status, body) {
  return Promise.resolve(
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('inicia sesión con una cuenta de demo y guarda el token', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      jsonResponse(200, {
        token: 'jwt-de-prueba',
        user: { id: 2, fullName: 'Lucía Fernández', role: 'AGENT' },
      }),
    );

    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Técnico' }));

    await waitFor(() => expect(screen.getByText('Inicio')).toBeInTheDocument());
    expect(localStorage.getItem('helpdesk.token')).toBe('jwt-de-prueba');

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/login');
    expect(JSON.parse(options.body)).toEqual({ email: 'lucia@helpdesk.dev', password: 'Agent123!' });
  });

  it('muestra el error que devuelve la API', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      jsonResponse(401, { title: 'Credenciales inválidas', detail: 'Email o contraseña incorrectos' }),
    );

    renderLogin();
    await userEvent.type(screen.getByLabelText('Email'), 'alguien@test.dev');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'incorrecta');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email o contraseña incorrectos');
    expect(localStorage.getItem('helpdesk.token')).toBeNull();
  });

  it('avisa si el backend no responde', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Usuario' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor');
  });
});
