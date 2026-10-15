import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext.jsx';
import TicketsPage from './TicketsPage.jsx';

function renderTickets(url) {
  return render(
    <MemoryRouter initialEntries={[url]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <Routes>
          <Route path="/tickets" element={<TicketsPage />} />
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

const ticket = {
  id: 11,
  title: 'No anda la VPN',
  category: 'NETWORK',
  priority: 'HIGH',
  status: 'OPEN',
  requester: { id: 3, fullName: 'Martín Gómez' },
  assignee: null,
  createdAt: '2026-10-14T12:00:00Z',
  updatedAt: '2026-10-14T12:00:00Z',
  slaDueAt: '2026-10-14T20:00:00Z',
};

describe('TicketsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('si la página de la URL ya no existe, muestra la última en lugar de "No hay tickets"', async () => {
    // Por ejemplo, al volver a "?page=5" después de cerrar tickets: ahora hay 12 tickets en 2 páginas.
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      const page = Number(new URL(url, 'http://localhost').searchParams.get('page'));
      const content = page === 1 ? [ticket] : [];
      return jsonResponse(200, { content, page, size: 10, totalElements: 12, totalPages: 2 });
    });

    renderTickets('/tickets?page=5');

    expect(await screen.findByText('No anda la VPN')).toBeInTheDocument();
    expect(screen.getByText(/página 2 de 2/)).toBeInTheDocument();
    expect(screen.queryByText('No hay tickets para mostrar')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([url]) => new URL(url, 'http://localhost').searchParams.get('page'))).toEqual([
      '5',
      '1',
    ]);
  });

  it('sin tickets muestra el estado vacío', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      jsonResponse(200, { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }),
    );

    renderTickets('/tickets');

    expect(await screen.findByText('No hay tickets para mostrar')).toBeInTheDocument();
  });
});
