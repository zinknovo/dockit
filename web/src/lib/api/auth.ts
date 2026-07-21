import { http } from '../http'

export async function fetchLogin(params: Api.Auth.LoginParams) {
  const backendParams = {
    phone: params.userName,
    password: params.password
  }

  const data = await http.post<{
    token: string
    user: {
      id: number
      name: string
      phone: string
      email?: string
      deptId: number
      role?: string
      status: number
    }
  }>({
    url: '/api/auth/login',
    params: backendParams
  })

  return {
    token: data.token,
    refreshToken: data.token,
    user: data.user
  }
}
