"use client";

import { useEffect, useState, useRef } from "react";
// @ts-ignore
import { GatewayEvent } from "@/types/event";

const MAX_EVENTS = 50; // keep the list bounded — don't grow forever

export function useGatewayEvents(wsUrl: string) {
    const [events, setEvents] = useState<GatewayEvent[]>([]);
    const [connected, setConnected] = useState(false);
    const wsRef = useRef<WebSocket | null>(null);

    useEffect(() => {
        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => setConnected(true);
        ws.onclose = () => setConnected(false);

        ws.onmessage = (message) => {
            const event: GatewayEvent = JSON.parse(message.data);
            setEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS));
        };

        return () => {
            ws.close();
        };
    }, [wsUrl]);

    return { events, connected };
}