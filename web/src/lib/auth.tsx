'use client';

import React, { startTransition, createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from './api';
import { useRouter } from 'next/navigation';

type User = {
  id: string;
  tenantId: string;
  fullName: string;
  email: string;
  role: string;
};

type AuthContextType = {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  login: (tenantCode: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    startTransition(() => {
    // Restore session from localStorage
    const savedToken = localStorage.getItem('access_token');
    const savedUser = localStorage.getItem('user');
    if (savedToken && savedUser) {
      setToken(savedToken);
      setUser(JSON.parse(savedUser));
    }
    setIsLoading(false);
    });
  }, []);

  useEffect(() => {
    // Le jeton est lu à chaque requête : si une autre fenêtre change de compte,
    // cette vue doit suivre au lieu d'afficher le nouvel établissement sous l'ancien.
    // `user` est écrit en dernier à la connexion et retiré en dernier à la déconnexion.
    const syncSession = (event: StorageEvent) => {
      if (event.key !== null && event.key !== 'user') return;
      const savedToken = localStorage.getItem('access_token');
      const savedUser = localStorage.getItem('user');
      setToken(savedToken);
      setUser(savedToken && savedUser ? JSON.parse(savedUser) : null);
    };
    window.addEventListener('storage', syncSession);
    return () => window.removeEventListener('storage', syncSession);
  }, []);

  const login = useCallback(async (tenantCode: string, email: string, password: string) => {
    const response = await authApi.login(tenantCode, email, password);
    const { accessToken, user: userData } = response.data;

    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('user', JSON.stringify(userData));

    setToken(accessToken);
    setUser(userData);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
    router.push('/login');
  }, [router]);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isLoading,
        login,
        logout,
        isAuthenticated: !!token && !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
