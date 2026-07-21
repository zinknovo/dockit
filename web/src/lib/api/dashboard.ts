import { http } from '../http'

export async function fetchDashboardStats() {
  try {
    return await http.get<{
      userCount: number
      departmentCount: number
      policyCount: number
      activePolicyCount: number
    }>({
      url: '/api/dashboard/stats'
    })
  } catch {
    return {
      userCount: 0,
      departmentCount: 0,
      policyCount: 0,
      activePolicyCount: 0
    }
  }
}
