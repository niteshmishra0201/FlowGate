"use client";

import { useGatewayEvents } from "@/hooks/useGatewayEvents";

export default function Dashboard() {
  const { events, connected } = useGatewayEvents("ws://localhost:8080/ws/events");

  const totalRequests = events.filter((e) => e.type === "request_completed").length;
  const cacheHits = events.filter((e) => e.type === "request_completed" && e.cacheHit).length;
  const rejections = events.filter((e) => e.type === "rate_limit_rejected").length;
  const cacheHitRate = totalRequests > 0 ? ((cacheHits / totalRequests) * 100).toFixed(1) : "0.0";

  return (
      <main className="min-h-screen bg-gray-950 text-gray-100 p-8">
        <div className="flex items-center gap-3 mb-8">
          <h1 className="text-2xl font-bold">FlowGate Live Dashboard</h1>
          <span
              className={`px-2 py-1 rounded text-xs font-medium ${
                  connected ? "bg-green-900 text-green-300" : "bg-red-900 text-red-300"
              }`}
          >
          {connected ? "Connected" : "Disconnected"}
        </span>
        </div>

        <div className="grid grid-cols-3 gap-4 mb-8">
          <StatCard label="Recent Requests" value={totalRequests.toString()} />
          <StatCard label="Cache Hit Rate" value={`${cacheHitRate}%`} />
          <StatCard label="Rate Limit Rejections" value={rejections.toString()} />
        </div>

        <div className="bg-gray-900 rounded-lg p-4">
          <h2 className="text-lg font-semibold mb-4">Live Event Stream</h2>
          <div className="space-y-2">
            {events.map((event, i) => (
                <EventRow key={i} event={event} />
            ))}
            {events.length === 0 && (
                <p className="text-gray-500 text-sm">Waiting for traffic...</p>
            )}
          </div>
        </div>
      </main>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
      <div className="bg-gray-900 rounded-lg p-4">
        <p className="text-gray-400 text-sm">{label}</p>
        <p className="text-3xl font-bold mt-1">{value}</p>
      </div>
  );
}

function EventRow({ event }: { event: { type: string; routeId: string; status: number | null; cacheHit: boolean | null; detail: string | null; timestamp: string } }) {
  const color =
      event.type === "circuit_breaker_transition"
          ? "text-orange-400"
          : event.type === "rate_limit_rejected"
              ? "text-red-400"
              : "text-green-400";

  return (
      <div className="flex items-center gap-3 text-sm font-mono border-b border-gray-800 pb-2">
        <span className="text-gray-500">{new Date(event.timestamp).toLocaleTimeString()}</span>
        <span className={color}>{event.type}</span>
        <span className="text-gray-300">{event.routeId}</span>
        {event.status && <span className="text-gray-400">status={event.status}</span>}
        {event.cacheHit !== null && <span className="text-gray-400">cache={event.cacheHit ? "HIT" : "MISS"}</span>}
        {event.detail && <span className="text-gray-400">{event.detail}</span>}
      </div>
  );
}