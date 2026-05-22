export interface HeimdallSpan {
  id: string
  parentId: string | null
  className: string
  methodName: string
  layer: string
  startTime: number
  duration: number
  error: string | null
  tags: Record<string, string> | null
}

export interface HeimdallTrace {
  id: string
  httpMethod: string
  path: string
  status: number
  startTime: number
  duration: number
  timestamp: string
  requestHeaders: Record<string, string>
  queryParams: Record<string, string>
  cookies: Record<string, string>
  requestBody: string | null
  responseHeaders: Record<string, string>
  spans: HeimdallSpan[]
}