import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, tokenStorage } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(() => Boolean(tokenStorage.get()));

  const logout = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
  }, []);

  // Si hay un token guardado, validamos que siga vigente pidiendo el usuario actual.
  useEffect(() => {
    if (!tokenStorage.get()) return;
    api('/api/auth/me')
      .then(setUser)
      .catch(logout)
      .finally(() => setLoading(false));
  }, [logout]);

  useEffect(() => {
    window.addEventListener('auth:expired', logout);
    return () => window.removeEventListener('auth:expired', logout);
  }, [logout]);

  const startSession = useCallback((response) => {
    tokenStorage.set(response.token);
    setUser(response.user);
    return response.user;
  }, []);

  const login = useCallback(
    (email, password) => api('/api/auth/login', { method: 'POST', body: { email, password } }).then(startSession),
    [startSession],
  );

  const register = useCallback(
    (data) => api('/api/auth/register', { method: 'POST', body: data }).then(startSession),
    [startSession],
  );

  const value = useMemo(() => {
    const isStaff = user?.role === 'AGENT' || user?.role === 'ADMIN';
    return { user, loading, isStaff, isAdmin: user?.role === 'ADMIN', login, register, logout };
  }, [user, loading, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return context;
}
