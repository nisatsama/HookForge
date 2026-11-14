
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  ArrowUpRight,
  CheckCircle2,
  ChevronRight,
  CircleAlert,
  Code2,
  Copy,
  KeyRound,
  Layers3,
  RefreshCw,
  Search,
  ShieldCheck,
  Webhook,
  X,
} from 'lucide-react';

import { demoApiService } from '../api/demoClient';
import type {
  DemoAttempt,
  DemoDelivery,
  DemoEndpoint,
  DemoEvent,
  DemoMetrics,
  DemoOverview,
} from '../types/demo';

type Tab = 'overview' | 'deliveries' | 'endpoints' | 'events';

const emptyMetrics: DemoMetrics = {
  total: 0,
  pending: 0,
  processing: 0,
  retrying: 0,
  succeeded: 0,
  failed: 0,
  dead: 0,
};

function formatDate(value?: string | null) {
  if (!value) return '—';

  return new Date(value).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function shortId(value: string) {
  return value.length > 14
    ? `${value.slice(0, 8)}...${value.slice(-4)}`
    : value;
}

function Status({ value }: { value: string }) {
  const status = value.toLowerCase();

  return (
    <span className={`demo-status demo-status-${status}`}>
      <span className="demo-status-dot" />
      {value}
    </span>
  );
}

function MetricCard({
  label,
  value,
  icon,
  detail,
}: {
  label: string;
  value: number;
  icon: React.ReactNode;
  detail: string;
}) {
  return (
    <div className="demo-metric-card">
      <div className="demo-metric-top">
        <span>{label}</span>
        <span className="demo-metric-icon">{icon}</span>
      </div>
      <div className="demo-metric-value">{value.toLocaleString('en-IN')}</div>
      <div className="demo-metric-detail">{detail}</div>
    </div>
  );
}

function JsonViewer({ value }: { value: unknown }) {
  return (
    <pre className="demo-json">
      {JSON.stringify(value, null, 2)}
    </pre>
  );
}

export default function DemoPage() {
  const [tab, setTab] = useState<Tab>('overview');
  const [overview, setOverview] = useState<DemoOverview | null>(null);
  const [endpoints, setEndpoints] = useState<DemoEndpoint[]>([]);
  const [events, setEvents] = useState<DemoEvent[]>([]);
  const [deliveries, setDeliveries] = useState<DemoDelivery[]>([]);
  const [selectedDelivery, setSelectedDelivery] =
    useState<DemoDelivery | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<DemoEvent | null>(null);
  const [attempts, setAttempts] = useState<DemoAttempt[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState('');

  const loadData = async () => {
    setLoading(true);
    setError('');

    try {
      const [overviewData, endpointData, eventData, deliveryData] =
        await Promise.all([
          demoApiService.getOverview(),
          demoApiService.getEndpoints(),
          demoApiService.getEvents(0, 20),
          demoApiService.getDeliveries(0, 20),
        ]);

      setOverview(overviewData);
      setEndpoints(endpointData);
      setEvents(eventData.items);
      setDeliveries(deliveryData.items);
    } catch {
      setError(
        'The demo environment could not be reached. Please try refreshing the page.'
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  const loadDeliveries = async (status: string) => {
    setStatusFilter(status);

    try {
      const result = await demoApiService.getDeliveries(
        0,
        20,
        status || undefined
      );
      setDeliveries(result.items);
    } catch {
      setError('Could not load deliveries. Please try again.');
    }
  };

  const openDelivery = async (delivery: DemoDelivery) => {
    setSelectedDelivery(delivery);
    setSelectedEvent(null);
    setDetailLoading(true);
    setAttempts([]);

    try {
      const result = await demoApiService.getAttempts(delivery.id);
      setAttempts(result);
    } catch {
      setAttempts([]);
    } finally {
      setDetailLoading(false);
    }
  };

  const filteredDeliveries = useMemo(() => {
    const query = search.trim().toLowerCase();

    if (!query) return deliveries;

    return deliveries.filter((delivery) =>
      [
        delivery.id,
        delivery.eventId,
        delivery.endpointName,
        delivery.eventType,
        delivery.status,
      ]
        .join(' ')
        .toLowerCase()
        .includes(query)
    );
  }, [deliveries, search]);

  const filteredEvents = useMemo(() => {
    const query = search.trim().toLowerCase();

    if (!query) return events;

    return events.filter((event) =>
      [event.id, event.eventType, JSON.stringify(event.payload)]
        .join(' ')
        .toLowerCase()
        .includes(query)
    );
  }, [events, search]);

  const metrics = overview?.metrics ?? emptyMetrics;

  const closeDetail = () => {
    setSelectedDelivery(null);
    setSelectedEvent(null);
    setAttempts([]);
  };

  const copyText = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      // Clipboard access may be unavailable in restricted browsers.
    }
  };

  return (
    <div className="demo-shell">
      <header className="demo-topbar">
        <Link to="/demo" className="demo-brand">
          <span className="demo-brand-mark">
            <Webhook size={21} />
          </span>
          <span>
            <strong>HookForge</strong>
            <small>WEBHOOK INFRASTRUCTURE</small>
          </span>
        </Link>

        <div className="demo-topbar-right">
          <span className="demo-live-label">
            <span className="demo-live-dot" />
            PUBLIC DEMO
          </span>
          <Link to="/" className="demo-login-button">
            <KeyRound size={16} />
            Login with API Key
            <ArrowUpRight size={15} />
          </Link>
        </div>
      </header>

      <main className="demo-main">
        <section className="demo-hero">
          <div className="demo-hero-copy">
            <div className="demo-eyebrow">
              <span className="demo-eyebrow-line" />
              EVENT-DRIVEN WEBHOOK PLATFORM
            </div>

            <h1>
              Reliable webhooks.
              <br />
              <span>Without the guesswork.</span>
            </h1>

            <p>
              Explore a live-style HookForge environment with sample events,
              delivery attempts, retry states, and endpoint activity.
              Everything here is isolated and read-only.
            </p>

            <div className="demo-hero-actions">
              <button
                className="demo-primary-button"
                onClick={() => setTab('overview')}
              >
                Explore dashboard
                <ChevronRight size={17} />
              </button>
              <span className="demo-readonly-note">
                <ShieldCheck size={15} />
                Isolated demo data · No real webhooks
              </span>
            </div>
          </div>

          <div className="demo-hero-card">
            <div className="demo-hero-card-header">
              <div>
                <span className="demo-card-kicker">SYSTEM STATUS</span>
                <h3>Delivery pipeline</h3>
              </div>
              <span className="demo-healthy-pill">
                <span className="demo-live-dot" />
                Operational
              </span>
            </div>

            <div className="demo-pipeline">
              <div className="demo-pipeline-step">
                <span className="demo-pipeline-icon">
                  <Code2 size={18} />
                </span>
                <div>
                  <strong>Event created</strong>
                  <small>Incoming application event</small>
                </div>
                <CheckCircle2 size={17} className="demo-success-icon" />
              </div>
              <div className="demo-pipeline-connector" />
              <div className="demo-pipeline-step">
                <span className="demo-pipeline-icon">
                  <Layers3 size={18} />
                </span>
                <div>
                  <strong>Delivery queued</strong>
                  <small>Event routed to subscribed endpoints</small>
                </div>
                <CheckCircle2 size={17} className="demo-success-icon" />
              </div>
              <div className="demo-pipeline-connector" />
              <div className="demo-pipeline-step">
                <span className="demo-pipeline-icon">
                  <RefreshCw size={18} />
                </span>
                <div>
                  <strong>Retry management</strong>
                  <small>Failed deliveries handled automatically</small>
                </div>
                <Activity size={17} className="demo-purple-icon" />
              </div>
            </div>

            <div className="demo-hero-card-footer">
              <span>Sample environment</span>
              <span>Fixture-backed · Read-only</span>
            </div>
          </div>
        </section>

        <section className="demo-dashboard">
          <div className="demo-section-heading">
            <div>
              <div className="demo-eyebrow">
                <span className="demo-eyebrow-line" />
                INTERACTIVE WALKTHROUGH
              </div>
              <h2>Explore the platform</h2>
              <p>Inspect sample infrastructure and delivery activity.</p>
            </div>

            <button
              className="demo-refresh-button"
              onClick={() => void loadData()}
              disabled={loading}
            >
              <RefreshCw size={15} className={loading ? 'demo-spin' : ''} />
              Refresh data
            </button>
          </div>

          <nav className="demo-tabs">
            {(
              [
                ['overview', 'Overview'],
                ['deliveries', 'Deliveries'],
                ['endpoints', 'Endpoints'],
                ['events', 'Events'],
              ] as [Tab, string][]
            ).map(([value, label]) => (
              <button
                key={value}
                className={tab === value ? 'demo-tab active' : 'demo-tab'}
                onClick={() => {
                  setTab(value);
                  setSearch('');
                  closeDetail();
                }}
              >
                {label}
              </button>
            ))}
          </nav>

          {error && (
            <div className="demo-error">
              <CircleAlert size={17} />
              <span>{error}</span>
              <button onClick={() => void loadData()}>Retry</button>
            </div>
          )}

          {loading ? (
            <div className="demo-loading">
              <RefreshCw size={22} className="demo-spin" />
              Loading demo environment...
            </div>
          ) : (
            <>
              {tab === 'overview' && (
                <>
                  <div className="demo-metrics-grid">
                    <MetricCard
                      label="Total deliveries"
                      value={metrics.total}
                      detail="Across the sample environment"
                      icon={<Activity size={17} />}
                    />
                    <MetricCard
                      label="Succeeded"
                      value={metrics.succeeded}
                      detail="Successfully delivered"
                      icon={<CheckCircle2 size={17} />}
                    />
                    <MetricCard
                      label="Retrying"
                      value={metrics.retrying}
                      detail="Waiting for another attempt"
                      icon={<RefreshCw size={17} />}
                    />
                    <MetricCard
                      label="Failed / Dead"
                      value={metrics.failed + metrics.dead}
                      detail="Unsuccessful deliveries"
                      icon={<CircleAlert size={17} />}
                    />
                  </div>

                  <div className="demo-overview-grid">
                    <section className="demo-panel demo-recent-panel">
                      <div className="demo-panel-heading">
                        <div>
                          <h3>Recent deliveries</h3>
                          <p>Latest delivery records in the demo</p>
                        </div>
                        <button
                          className="demo-text-button"
                          onClick={() => setTab('deliveries')}
                        >
                          View all <ArrowUpRight size={14} />
                        </button>
                      </div>

                      <DeliveryTable
                        deliveries={overview?.recentDeliveries ?? []}
                        onSelect={openDelivery}
                      />
                    </section>

                    <section className="demo-panel demo-endpoint-panel">
                      <div className="demo-panel-heading">
                        <div>
                          <h3>Registered endpoints</h3>
                          <p>Sample webhook destinations</p>
                        </div>
                        <span className="demo-count">
                          {endpoints.length}
                        </span>
                      </div>

                      <EndpointList endpoints={endpoints.slice(0, 4)} />
                      <button
                        className="demo-panel-link"
                        onClick={() => setTab('endpoints')}
                      >
                        Explore endpoints <ChevronRight size={15} />
                      </button>
                    </section>
                  </div>
                </>
              )}

              {tab === 'deliveries' && (
                <section className="demo-panel">
                  <div className="demo-panel-heading demo-list-heading">
                    <div>
                      <h3>Delivery records</h3>
                      <p>Inspect statuses and sample delivery attempts.</p>
                    </div>
                    <div className="demo-list-controls">
                      <div className="demo-search">
                        <Search size={15} />
                        <input
                          value={search}
                          onChange={(event) => setSearch(event.target.value)}
                          placeholder="Search deliveries..."
                        />
                      </div>
                      <select
                        value={statusFilter}
                        onChange={(event) =>
                          void loadDeliveries(event.target.value)
                        }
                        aria-label="Filter delivery status"
                      >
                        <option value="">All statuses</option>
                        <option value="PENDING">Pending</option>
                        <option value="PROCESSING">Processing</option>
                        <option value="RETRYING">Retrying</option>
                        <option value="SUCCEEDED">Succeeded</option>
                        <option value="FAILED">Failed</option>
                        <option value="DEAD">Dead</option>
                      </select>
                    </div>
                  </div>

                  <DeliveryTable
                    deliveries={filteredDeliveries}
                    onSelect={openDelivery}
                  />
                </section>
              )}

              {tab === 'endpoints' && (
                <section className="demo-panel">
                  <div className="demo-panel-heading">
                    <div>
                      <h3>Webhook endpoints</h3>
                      <p>
                        Destinations subscribed to receive sample events.
                      </p>
                    </div>
                    <span className="demo-count">{endpoints.length} total</span>
                  </div>
                  <EndpointList endpoints={endpoints} expanded />
                </section>
              )}

              {tab === 'events' && (
                <section className="demo-panel">
                  <div className="demo-panel-heading demo-list-heading">
                    <div>
                      <h3>Event explorer</h3>
                      <p>Inspect the sample event types and JSON payloads.</p>
                    </div>
                    <div className="demo-search">
                      <Search size={15} />
                      <input
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                        placeholder="Search events..."
                      />
                    </div>
                  </div>

                  <div className="demo-event-list">
                    {filteredEvents.map((event) => (
                      <button
                        key={event.id}
                        className="demo-event-row"
                        onClick={() => {
                          setSelectedEvent(event);
                          setSelectedDelivery(null);
                        }}
                      >
                        <span className="demo-event-icon">
                          <Code2 size={17} />
                        </span>
                        <span className="demo-event-info">
                          <strong>{event.eventType}</strong>
                          <small>{shortId(event.id)}</small>
                        </span>
                        <span className="demo-event-date">
                          {formatDate(event.createdAt)}
                        </span>
                        <ChevronRight size={17} />
                      </button>
                    ))}

                    {filteredEvents.length === 0 && (
                      <div className="demo-empty">No matching events.</div>
                    )}
                  </div>
                </section>
              )}
            </>
          )}
        </section>

        <section className="demo-cta">
          <div className="demo-cta-icon">
            <KeyRound size={22} />
          </div>
          <div>
            <h3>Ready to connect your application?</h3>
            <p>
              Sign in with your HookForge API key to access your own
              authenticated workspace.
            </p>
          </div>
          <Link to="/" className="demo-primary-button">
            Login with API Key
            <ArrowUpRight size={16} />
          </Link>
        </section>

        <footer className="demo-footer">
          <span>HookForge · Webhook delivery infrastructure</span>
          <span>Public demonstration environment</span>
        </footer>
      </main>

      {(selectedDelivery || selectedEvent) && (
        <div className="demo-modal-backdrop" onClick={closeDetail}>
          <section
            className="demo-detail-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="demo-detail-header">
              <div>
                <span className="demo-card-kicker">
                  {selectedDelivery ? 'DELIVERY INSPECTOR' : 'EVENT INSPECTOR'}
                </span>
                <h3>
                  {selectedDelivery
                    ? selectedDelivery.eventType
                    : selectedEvent?.eventType}
                </h3>
              </div>
              <button
                className="demo-close-button"
                onClick={closeDetail}
                aria-label="Close details"
              >
                <X size={19} />
              </button>
            </div>

            {selectedDelivery && (
              <>
                <div className="demo-detail-summary">
                  <div>
                    <span>Status</span>
                    <Status value={selectedDelivery.status} />
                  </div>
                  <div>
                    <span>Attempts</span>
                    <strong>{selectedDelivery.attemptCount}</strong>
                  </div>
                  <div>
                    <span>Endpoint</span>
                    <strong>{selectedDelivery.endpointName}</strong>
                  </div>
                </div>

                <div className="demo-detail-block">
                  <h4>Delivery information</h4>
                  <DetailRow label="Delivery ID" value={selectedDelivery.id} />
                  <DetailRow label="Event ID" value={selectedDelivery.eventId} />
                  <DetailRow
                    label="Created"
                    value={formatDate(selectedDelivery.createdAt)}
                  />
                  <DetailRow
                    label="Last attempt"
                    value={formatDate(selectedDelivery.lastAttemptAt)}
                  />
                  <DetailRow
                    label="Delivered at"
                    value={formatDate(selectedDelivery.deliveredAt)}
                  />
                </div>

                <div className="demo-detail-block">
                  <h4>Attempt history</h4>
                  {detailLoading ? (
                    <div className="demo-inline-loading">
                      <RefreshCw size={15} className="demo-spin" />
                      Loading attempts...
                    </div>
                  ) : attempts.length === 0 ? (
                    <p className="demo-muted">No attempt records available.</p>
                  ) : (
                    <div className="demo-attempt-list">
                      {attempts.map((attempt) => (
                        <div className="demo-attempt" key={attempt.id}>
                          <div className="demo-attempt-heading">
                            <strong>Attempt #{attempt.attemptNumber}</strong>
                            <span>{formatDate(attempt.startedAt)}</span>
                          </div>
                          <div className="demo-attempt-meta">
                            <span>
                              HTTP {attempt.statusCode ?? '—'}
                            </span>
                            <span>
                              {attempt.durationMs != null
                                ? `${attempt.durationMs} ms`
                                : 'Duration unavailable'}
                            </span>
                          </div>
                          {attempt.errorMessage && (
                            <p className="demo-attempt-error">
                              {attempt.errorMessage}
                            </p>
                          )}
                          {attempt.responseBody && (
                            <pre className="demo-json demo-response">
                              {attempt.responseBody}
                            </pre>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            )}

            {selectedEvent && (
              <div className="demo-detail-block">
                <div className="demo-payload-heading">
                  <h4>Event payload</h4>
                  <button
                    className="demo-copy-button"
                    onClick={() =>
                      void copyText(JSON.stringify(selectedEvent.payload, null, 2))
                    }
                  >
                    <Copy size={14} /> Copy JSON
                  </button>
                </div>
                <DetailRow label="Event ID" value={selectedEvent.id} />
                <DetailRow
                  label="Created"
                  value={formatDate(selectedEvent.createdAt)}
                />
                <JsonViewer value={selectedEvent.payload} />
              </div>
            )}

            <div className="demo-modal-footer">
              <ShieldCheck size={15} />
              Sample data only. Actions are disabled in this environment.
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

function DeliveryTable({
  deliveries,
  onSelect,
}: {
  deliveries: DemoDelivery[];
  onSelect: (delivery: DemoDelivery) => void;
}) {
  if (deliveries.length === 0) {
    return <div className="demo-empty">No delivery records found.</div>;
  }

  return (
    <div className="demo-table-wrap">
      <table className="demo-table">
        <thead>
          <tr>
            <th>Event</th>
            <th>Endpoint</th>
            <th>Status</th>
            <th>Attempts</th>
            <th>Created</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {deliveries.map((delivery) => (
            <tr
              key={delivery.id}
              onClick={() => onSelect(delivery)}
              className="demo-clickable-row"
            >
              <td>
                <strong>{delivery.eventType}</strong>
                <small>{shortId(delivery.id)}</small>
              </td>
              <td>{delivery.endpointName}</td>
              <td><Status value={delivery.status} /></td>
              <td>{delivery.attemptCount}</td>
              <td>{formatDate(delivery.createdAt)}</td>
              <td><ChevronRight size={16} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function EndpointList({
  endpoints,
  expanded = false,
}: {
  endpoints: DemoEndpoint[];
  expanded?: boolean;
}) {
  if (endpoints.length === 0) {
    return <div className="demo-empty">No endpoints available.</div>;
  }

  return (
    <div className="demo-endpoint-list">
      {endpoints.map((endpoint) => (
        <div className="demo-endpoint-row" key={endpoint.id}>
          <span className="demo-endpoint-icon">
            <Webhook size={17} />
          </span>
          <div className="demo-endpoint-info">
            <strong>{endpoint.name}</strong>
            <small>{endpoint.url}</small>
            {expanded && (
              <small className="demo-endpoint-created">
                Added {formatDate(endpoint.createdAt)}
              </small>
            )}
          </div>
          <Status value={endpoint.status} />
        </div>
      ))}
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="demo-detail-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
