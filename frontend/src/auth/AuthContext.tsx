import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import {
  getRefreshToken, hasStoredSession, refreshSession, setSessionExpiredHandler, storeTokens,
} from '../api/client';
import type { TokenResponse, User } from '../api/types';

type Status = 'loading' | 'anonymous' | 'authenticated';

interface AuthApi {
  status: Status;
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  register: (b: Parameters<typeof api.register>[0]) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthApi | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>(hasStoredSession() ? 'loading' : 'anonymous');
  const [user, setUser] = useState<User | null>(null);

  const signedOut = useCallback(() => {
    queryClient.clear();
    setUser(null);
    setStatus('anonymous');
  }, [queryClient]);

  // The API client tells us when a refresh failed (expired or revoked): drop back to the login screen.
  useEffect(() => {
    setSessionExpiredHandler(signedOut);
    return () => setSessionExpiredHandler(null);
  }, [signedOut]);

  // Restore the session after a page reload using the stored refresh token.
  useEffect(() => {
    if (!hasStoredSession()) return;
    let cancelled = false;
    refreshSession()
      .then(() => api.me())
      .then((me) => { if (!cancelled) { setUser(me); setStatus('authenticated'); } })
      .catch(() => { if (!cancelled) signedOut(); });
    return () => { cancelled = true; };
  }, [signedOut]);

  const startSession = useCallback(async (tokens: TokenResponse) => {
    storeTokens(tokens);
    const me = await api.me();
    queryClient.clear();
    setUser(me);
    setStatus('authenticated');
  }, [queryClient]);

  const value = useMemo<AuthApi>(() => ({
    status,
    user,
    login: async (email, password) => startSession(await api.login(email, password)),
    register: async (b) => startSession(await api.register(b)),
    logout: async () => {
      const token = getRefreshToken();
      storeTokens(null);
      signedOut();
      // tell the server too, so a copied refresh token stops working; failing to reach it must not block sign-out
      if (token) api.logout(token).catch(() => undefined);
    },
  }), [status, user, startSession, signedOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthApi {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
