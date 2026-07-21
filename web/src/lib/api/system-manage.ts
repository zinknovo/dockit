import { http } from '../http'
import { adaptPageResult, adaptUserItem, adaptDeptItem } from '../api-adapter'

export async function fetchGetUserList(params: Api.SystemManage.UserSearchParams) {
  const backendParams: any = {
    pageNum: params.current || 1,
    pageSize: params.size || 20
  }

  if (params.userName) backendParams.name = params.userName
  if (params.id) backendParams.deptId = params.id

  const data = await http.get<{
    count: number
    pageNo: number
    pageSize: number
    lists: Array<{
      id: number
      name: string
      phone: string
      email?: string
      deptId: number
      deptName?: string
      role?: string
      status: number
    }>
  }>({
    url: '/users',
    params: backendParams
  })

  const adaptedPage = adaptPageResult(data)
  return {
    ...adaptedPage,
    records: data.lists.map(adaptUserItem)
  }
}

export async function fetchGetDeptList(params?: { current?: number; size?: number; name?: string }) {
  const backendParams: any = {
    pageNum: params?.current || 1,
    pageSize: params?.size || 20
  }

  if (params?.name) backendParams.name = params.name

  const data = await http.get<{
    count: number
    pageNo: number
    pageSize: number
    lists: Array<{
      id: number
      name: string
      parentId?: number
    }>
  }>({
    url: '/departments',
    params: backendParams
  })

  const adaptedPage = adaptPageResult(data)
  return {
    ...adaptedPage,
    records: data.lists.map(adaptDeptItem)
  }
}

export function fetchAddUser(data: Api.SystemManage.UserAddParams) {
  return http.post<boolean>({
    url: '/users',
    params: {
      name: data.name,
      phone: data.phone,
      email: data.email,
      deptId: data.deptId,
      role: data.role,
      status: data.status || 1
    }
  })
}

export function fetchAddDept(data: Api.SystemManage.DeptAddParams) {
  return http.post<boolean>({
    url: '/departments',
    params: {
      name: data.name,
      parentId: data.parentId
    }
  })
}

export function fetchGetRoleList(params: Api.SystemManage.RoleSearchParams) {
  return http.get<Api.SystemManage.RoleList>({
    url: '/api/role/list',
    params
  })
}

export function fetchGetMenuList() {
  return http.get<any>({
    url: '/api/v3/system/menus'
  })
}
