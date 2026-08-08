export type GatewayEventType =
    | "request_completed"
    | "rate_limit_rejected"
    | "circuit_breaker_transition";

export interface GatewayEvent {
    type: GatewayEventType;
    routeId: string;
    status: number | null;
    cacheHit: boolean | null;
    detail: string | null;
    timestamp: string;
}