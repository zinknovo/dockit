/**
 * API 接口类型定义模块（Next.js 版）
 */

declare namespace Api {
  /** 通用类型 */
  namespace Common {
    /** 分页参数 */
    interface PaginationParams {
      current: number
      size: number
      total: number
    }

    /** 通用搜索参数 */
    type CommonSearchParams = Pick<PaginationParams, 'current' | 'size'>

    /** 分页响应基础结构 */
    interface PaginatedResponse<T = any> {
      records: T[]
      current: number
      size: number
      total: number
    }

  }

  /** 认证类型 */
  namespace Auth {
    interface LoginParams {
      userName: string
      password: string
    }

  }

  /** 系统管理类型 */
  namespace SystemManage {
    interface UserListItem {
      id: number
      avatar: string
      status: string
      userName: string
      userGender: string
      nickName: string
      userPhone: string
      userEmail: string
      userRoles: string[]
      createBy: string
      createTime: string
      updateBy: string
      updateTime: string
    }

    type UserSearchParams = Partial<
      Pick<UserListItem, 'id' | 'userName' | 'userGender' | 'userPhone' | 'userEmail' | 'status'> &
        Api.Common.CommonSearchParams
    >

    interface RoleListItem {
      roleId: number
      roleName: string
      roleCode: string
      description: string
      enabled: boolean
      createTime: string
    }

    interface DeptListItem {
      id: number
      name: string
      parentId?: number
    }

    interface DeptAddParams {
      name: string
      parentId?: number
    }

    interface UserAddParams {
      name: string
      phone: string
      email?: string
      deptId: number
      role?: string
      status?: number
    }
  }

  /** 制度管理类型 */
  namespace Policy {
    interface PolicyListItem {
      id: number
      name: string
      code: string
      type: number
      typeText: string
      currentVersion: string
      status: number
      statusText: string
      ownerDeptId: number
      ownerDeptName: string
      effectiveDate: string
      expiryDate?: string
      creator: string
      createdAt: string
      updatedAt: string
    }

    interface PolicySearchParams extends Api.Common.CommonSearchParams {
      name?: string
      type?: number
      status?: number
    }

    interface PolicyDetail extends PolicyListItem {
      contentSummary?: string
      contentFilePath?: string
    }

    interface PolicyVersionHistory {
      id: number
      version: string
      status: number
      statusText: string
      createdAt: string
      creator: string
    }
  }
}
