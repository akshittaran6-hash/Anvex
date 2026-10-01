const TOKEN_KEY = 'anvex.apiToken'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem('anvex.sessionToken') || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
  sessionStorage.removeItem('anvex.sessionToken')
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

async function request(method, path, body) {
  const headers = {}
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })

  let data = null
  const text = await response.text()
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }

  if (!response.ok) {
    const error = new Error((data && data.error) || `Request failed (${response.status})`)
    error.status = response.status
    throw error
  }
  return data
}

export const api = {
  get: (path) => request('GET', path),
  put: (path, body) => request('PUT', path, body),
  post: (path, body) => request('POST', path, body)
}
