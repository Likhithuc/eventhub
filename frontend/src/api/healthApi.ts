import { http } from './http'

export interface HealthResponse {
  status: 'UP' | 'DEGRADED'
  service: string
  database: 'UP' | 'DOWN'
  timestamp: string
}

export function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return http<HealthResponse>('/api/v1/health', { signal })
}
