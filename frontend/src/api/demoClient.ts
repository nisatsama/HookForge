
import axios from 'axios';

import type {
  DemoAttempt,
  DemoDelivery,
  DemoEndpoint,
  DemoEvent,
  DemoOverview,
  DemoPage,
} from '../types/demo';

const demoApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 12000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Intentionally no Authorization interceptor or API-key credential.
export const demoApiService = {
  getOverview: async (): Promise<DemoOverview> => {
    const response = await demoApi.get<DemoOverview>(
      '/api/v1/demo/overview'
    );
    return response.data;
  },

  getEndpoints: async (): Promise<DemoEndpoint[]> => {
    const response = await demoApi.get<DemoEndpoint[]>(
      '/api/v1/demo/endpoints'
    );
    return response.data;
  },

  getEvents: async (
    page = 0,
    size = 10
  ): Promise<DemoPage<DemoEvent>> => {
    const response = await demoApi.get<DemoPage<DemoEvent>>(
      '/api/v1/demo/events',
      { params: { page, size } }
    );
    return response.data;
  },

  getEvent: async (id: string): Promise<DemoEvent> => {
    const response = await demoApi.get<DemoEvent>(
      `/api/v1/demo/events/${encodeURIComponent(id)}`
    );
    return response.data;
  },

  getDeliveries: async (
    page = 0,
    size = 10,
    status?: string
  ): Promise<DemoPage<DemoDelivery>> => {
    const response = await demoApi.get<DemoPage<DemoDelivery>>(
      '/api/v1/demo/deliveries',
      { params: { page, size, ...(status ? { status } : {}) } }
    );
    return response.data;
  },

  getDelivery: async (id: string): Promise<DemoDelivery> => {
    const response = await demoApi.get<DemoDelivery>(
      `/api/v1/demo/deliveries/${encodeURIComponent(id)}`
    );
    return response.data;
  },

  getAttempts: async (id: string): Promise<DemoAttempt[]> => {
    const response = await demoApi.get<DemoAttempt[]>(
      `/api/v1/demo/deliveries/${encodeURIComponent(id)}/attempts`
    );
    return response.data;
  },
};
