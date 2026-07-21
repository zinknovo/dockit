import axios, { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

export interface BaseResponse<T> {
  code: number
  msg?: string
  message?: string
  data: T
}

const REQUEST_TIMEOUT = 15000

const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL || ''
const withCredentials = process.env.NEXT_PUBLIC_WITH_CREDENTIALS === 'true'

const axiosInstance = axios.create({
  timeout: REQUEST_TIMEOUT,
  baseURL: apiBaseUrl,
  withCredentials,
  validateStatus: (status) => status >= 200 && status < 300,
  transformResponse: [
    (data, headers) => {
      const contentType = headers['content-type']
      if (contentType?.includes('application/json')) {
        try {
          return JSON.parse(data)
        } catch {
          return data
        }
      }
      return data
    }
  ]
})

function getAccessToken() {
  if (typeof window === 'undefined') return null
  return window.localStorage.getItem('complog-token')
}

axiosInstance.interceptors.request.use(
  (request: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token) request.headers.set('Authorization', token)

    if (request.data && !(request.data instanceof FormData) && !request.headers['Content-Type']) {
      request.headers.set('Content-Type', 'application/json')
      request.data = JSON.stringify(request.data)
    }

    return request
  },
  (error) => Promise.reject(error)
)

async function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  if (
    ['POST', 'PUT'].includes(config.method?.toUpperCase() || '') &&
    config.params &&
    !config.data
  ) {
    config.data = config.params
    config.params = undefined
  }

  const res = await axiosInstance.request<
    BaseResponse<T> | { code: number; msg?: string; message?: string; data?: T }
  >(config)
  const responseData = res.data as any

  if (responseData && typeof responseData.code === 'number') {
    if (responseData.code !== 200) {
      const message = responseData.msg || responseData.message || '请求失败'
      throw new Error(message)
    }
    return responseData.data as T
  }

  return res.data as T
}

export const http = {
  get<T>(config: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'GET' })
  },
  post<T>(config: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'POST' })
  },
  put<T>(config: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'PUT' })
  },
  del<T>(config: AxiosRequestConfig) {
    return request<T>({ ...config, method: 'DELETE' })
  },
  request<T>(config: AxiosRequestConfig) {
    return request<T>(config)
  }
}

export type { AxiosResponse }
