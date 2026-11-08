import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
type Auth = { token: string | null; signIn: (token: string) => void; signOut: () => void };
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) { const [token, setToken] = useState<string | null>(null); const value = useMemo(() => ({ token, signIn: (key: string) => setToken(key.trim()), signOut: () => setToken(null) }), [token]); return <Context.Provider value={value}>{children}</Context.Provider>; }
export function useAuth() { const auth = useContext(Context); if (!auth) throw new Error('AuthProvider missing'); return auth; }
