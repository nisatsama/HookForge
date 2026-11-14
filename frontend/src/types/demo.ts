
export interface DemoMetrics {
  total: number;
  pending: number;
  processing: number;
  retrying: number;
  succeeded: number;
  failed: number;
  dead: number;
}

export interface DemoEndpoint {
  id: string;
  name: string;
  url: string;
  status: 'ACTIVE' | 'DISABLED';
  createdAt: string;
}

export interface DemoEvent {
  id: string;
  eventType: string;
  payload: Record<string, unknown>;
  createdAt: string;
  created: boolean;
}

export interface DemoDelivery {
  id: string;
  eventId: string;
  endpointId: string;
  endpointName: string;
  eventType: string;
  status:
    | 'PENDING'
    | 'PROCESSING'
    | 'RETRYING'
    | 'SUCCEEDED'
    | 'FAILED'
    | 'DEAD';
  attemptCount: number;
  nextAttemptAt: string | null;
  lastAttemptAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DemoAttempt {
  id: string;
  attemptNumber: number;
  statusCode: number | null;
  responseBody: string | null;
  errorMessage: string | null;
  durationMs: number | null;
  startedAt: string;
  completedAt: string | null;
}

export interface DemoPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface DemoOverview {
  metrics: DemoMetrics;
  recentDeliveries: DemoDelivery[];
}
