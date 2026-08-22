const API = '/api'

export function getToken() { return localStorage.getItem('token') }
export function setToken(t) { localStorage.setItem('token', t) }
export function clearToken() { localStorage.removeItem('token') }

function authHeaders() {
  const token = getToken()
  return token
    ? { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
    : { 'Content-Type': 'application/json' }
}

export async function apiFetch(path, options = {}) {
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers: { ...authHeaders(), ...options.headers },
  })
  const json = await res.json().catch(() => null)
  if (res.status === 401) {
    clearToken()
    window.location.reload()
  }
  const data = (json && typeof json === 'object' && 'success' in json && 'data' in json && json.data !== undefined)
    ? json.data
    : json
  return { ok: res.ok, status: res.status, data, raw: json }
}

