export function adaptPageResult<T>(backendData: {
  count: number
  pageNo: number
  pageSize: number
  lists: T[]
}): {
  records: T[]
  current: number
  size: number
  total: number
} {
  return {
    records: backendData.lists || [],
    current: backendData.pageNo || 1,
    size: backendData.pageSize || 20,
    total: backendData.count || 0
  }
}

export function adaptUserItem(backendUser: {
  id: number
  name: string
  phone: string
  email?: string
  deptId: number
  deptName?: string
  role?: string
  status: number
}): {
  id: number
  userName: string
  userPhone: string
  userEmail: string
  userGender: string
  avatar: string
  status: string
  nickName: string
  userRoles: string[]
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
} {
  return {
    id: backendUser.id,
    userName: backendUser.name || '',
    userPhone: backendUser.phone || '',
    userEmail: backendUser.email || '',
    userGender: '未知',
    avatar: '',
    status: String(backendUser.status || 1),
    nickName: backendUser.name || '',
    userRoles: backendUser.role ? [backendUser.role] : [],
    createBy: '',
    createTime: '',
    updateBy: '',
    updateTime: ''
  }
}

export function adaptDeptItem(backendDept: { id: number; name: string; parentId?: number }): {
  id: number
  name: string
  parentId?: number
} {
  return {
    id: backendDept.id,
    name: backendDept.name,
    parentId: backendDept.parentId
  }
}
