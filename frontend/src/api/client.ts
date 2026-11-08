import axios from 'axios';
let credential: string | null = null;
export const setCredential = (value: string | null) => { credential = value; };
export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080', timeout: 12_000, headers: { 'Content-Type': 'application/json' } });
api.interceptors.request.use(config => { if (credential) config.headers.Authorization = `Bearer ${credential}`; return config; });
api.interceptors.response.use(response => response, error => { if (error.response?.status === 401) { credential = null; window.dispatchEvent(new Event('hookforge:unauthorized')); } return Promise.reject(error); });
export function messageOf(error: unknown) { if (axios.isAxiosError(error)) return error.response?.data?.message || error.response?.data?.error || (error.code === 'ECONNABORTED' ? 'The request timed out. Try again.' : error.message); return error instanceof Error ? error.message : 'Something went wrong.'; }
