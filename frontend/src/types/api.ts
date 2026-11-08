export type Endpoint = { id: string; name: string; url: string; status: 'ACTIVE' | 'DISABLED'; createdAt: string };
export type EventRecord = { id: string; eventType: string; payload: unknown; createdAt: string; created: boolean };
export type Page<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number; last: boolean };
export type Delivery = { id: string; eventId: string; endpointId: string; status: 'PENDING' | 'PROCESSING' | 'RETRYING' | 'SUCCEEDED' | 'FAILED' | 'DEAD'; attemptCount: number; nextAttemptAt: string | null; lastAttemptAt: string | null; deliveredAt: string | null; createdAt: string; updatedAt: string };
export type Attempt = { id: string; attemptNumber: number; statusCode: number | null; responseBody: string | null; errorMessage: string | null; durationMs: number | null; startedAt: string; completedAt: string | null };
export type Metrics = { total: number; pending: number; processing: number; retrying: number; succeeded: number; failed: number; dead: number };
export type ApiKey = { id: string; name: string; keyPrefix: string; createdAt: string; expiresAt: string | null; revokedAt: string | null; lastUsedAt: string | null };
export type CreatedApiKey = ApiKey & { secretKey: string };
