const API_URL = import.meta.env.VITE_API_URL ?? '';
const TOKEN_KEY = 'helpdesk.token';

/** Error con la información del ProblemDetail (RFC 7807) que devuelve el backend. */
export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Error ${status}`);
    this.status = status;
    this.title = problem?.title;
    this.fieldErrors = problem?.errors ?? {};
  }
}

export const tokenStorage = {
  get: () => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (token) => {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* el modo privado puede bloquear localStorage: la sesión dura lo que la pestaña */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* idem */
    }
  },
};

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' };
  const token = tokenStorage.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, { detail: 'No se pudo conectar con el servidor. ¿Está levantado el backend?' });
  }

  if (response.status === 204) return null;

  const isJson = response.headers.get('content-type')?.includes('json');
  const data = isJson ? await response.json() : null;

  if (!response.ok) {
    if (response.status === 401 && token) {
      // Token vencido o inválido: avisamos a la app para que cierre la sesión.
      window.dispatchEvent(new Event('auth:expired'));
    }
    throw new ApiError(response.status, data);
  }
  return data;
}

export const ticketsApi = {
  search: (params) => api(`/api/tickets?${new URLSearchParams(params)}`),
  get: (id) => api(`/api/tickets/${id}`),
  create: (ticket) => api('/api/tickets', { method: 'POST', body: ticket }),
  changeStatus: (id, status) => api(`/api/tickets/${id}/status`, { method: 'PATCH', body: { status } }),
  changePriority: (id, priority) => api(`/api/tickets/${id}/priority`, { method: 'PATCH', body: { priority } }),
  assign: (id, assigneeId) => api(`/api/tickets/${id}/assignee`, { method: 'PATCH', body: { assigneeId } }),
  comments: (id) => api(`/api/tickets/${id}/comments`),
  addComment: (id, comment) => api(`/api/tickets/${id}/comments`, { method: 'POST', body: comment }),
  events: (id) => api(`/api/tickets/${id}/events`),
};

export const usersApi = {
  list: () => api('/api/users'),
  agents: () => api('/api/users/agents'),
  changeRole: (id, role) => api(`/api/users/${id}/role`, { method: 'PATCH', body: { role } }),
};

export const dashboardApi = {
  get: () => api('/api/dashboard'),
};
