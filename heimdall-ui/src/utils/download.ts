// Client-side file export helpers — the data is already in the page, so files are
// generated in the browser instead of adding export endpoints to the backend.

export function downloadText(filename: string, content: string, mime = 'text/plain') {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function downloadJson(filename: string, data: unknown) {
  downloadText(filename, JSON.stringify(data, null, 2), 'application/json')
}

type CsvValue = string | number | boolean | null | undefined

function csvCell(value: CsvValue): string {
  if (value === null || value === undefined) return ''
  const s = String(value)
  // quote cells containing separators, quotes or line breaks (RFC 4180)
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function toCsv(headers: string[], rows: CsvValue[][]): string {
  return [headers, ...rows].map(row => row.map(csvCell).join(',')).join('\n')
}

export function downloadCsv(filename: string, headers: string[], rows: CsvValue[][]) {
  downloadText(filename, toCsv(headers, rows), 'text/csv')
}

/** e.g. "heimdall-metrics-20261026-153012" */
export function timestampedName(prefix: string, date = new Date()) {
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
    + `-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
  return `${prefix}-${stamp}`
}
