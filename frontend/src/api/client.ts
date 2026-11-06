import axios, { type InternalAxiosRequestConfig } from 'axios'

export interface AuthUser {
  id: number
  email: string
  fullName: string
  roles: string[]
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  user: AuthUser
}

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'

export const api = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 12_000,
  headers: { 'Content-Type': 'application/json' },
})

let accessToken: string | null = null
let refreshRequest: Promise<AuthResponse> | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function refreshSession(): Promise<AuthResponse> {
  if (!refreshRequest) {
    refreshRequest = api
      .post<AuthResponse>('/auth/refresh', {})
      .then(({ data }) => {
        setAccessToken(data.accessToken)
        return data
      })
      .finally(() => {
        refreshRequest = null
      })
  }
  return refreshRequest
}

api.interceptors.request.use((config) => {
  if (accessToken && !config.url?.startsWith('/auth/')) {
    config.headers.set('Authorization', 'Bearer ' + accessToken)
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)
    const original = error.config as RetryConfig | undefined
    if (!original || error.response?.status !== 401 || original._retry || original.url?.startsWith('/auth/')) {
      return Promise.reject(error)
    }

    original._retry = true
    try {
      const session = await refreshSession()
      original.headers.set('Authorization', 'Bearer ' + session.accessToken)
      return api(original)
    } catch (refreshError) {
      setAccessToken(null)
      return Promise.reject(refreshError)
    }
  },
)

export function getErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}
