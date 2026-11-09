import { afterEach, describe, expect, it } from 'vitest';
import { api, setCredential } from './client';

afterEach(() => setCredential(null));

describe('API credential interceptor', () => {
  it('sends the API key in a Bearer Authorization header', async () => {
    setCredential('hf_live_test-key');
    let authorization: unknown;

    await api.get('/api/v1/endpoints', {
      adapter: async config => {
        authorization = config.headers.Authorization;
        return { data: [], status: 200, statusText: 'OK', headers: {}, config };
      },
    });

    expect(authorization).toBe('Bearer hf_live_test-key');
  });
});
