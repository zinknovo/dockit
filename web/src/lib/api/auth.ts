import { http } from '../http'

export async function fetchLogin(params: Api.Auth.LoginParams) {
  const data = await http.post<{
    accessToken: string
    refreshToken: string
    user: {
      id: number
      username: string
      email: string
      phone: string
      role: string
      status: number
    }
  }>({
    url: '/api/users/login',
    data: {
      username: params.userName,
      password: params.password
    }
  })

  return {
    token: data.accessToken,
    refreshToken: data.refreshToken,
    user: {
      id: data.user.id,
      name: data.user.username,
      phone: data.user.phone || '',
      email: data.user.email || '',
      role: data.user.role || 'user',
      status: data.user.status
    }
  }
}

export async function fetchRegister(params: {
  username: string
  password: string
  email: string
  phone: string
}) {
  return http.post<unknown>({
    url: '/api/users/register',
    data: params
  })
}
