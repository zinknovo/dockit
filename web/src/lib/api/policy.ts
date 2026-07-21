import { http } from '../http'
import { adaptPageResult } from '../api-adapter'

export async function fetchGetPolicyList(params: Api.Policy.PolicySearchParams) {
  const backendParams: any = {
    pageNum: params.current || 1,
    pageSize: params.size || 20
  }

  if (params.name) backendParams.name = params.name
  if (params.type !== undefined) backendParams.type = params.type
  if (params.status !== undefined) backendParams.status = params.status

  const data = await http.get<{
    count: number
    pageNo: number
    pageSize: number
    lists: Api.Policy.PolicyListItem[]
  }>({
    url: '/policies',
    params: backendParams
  })

  const adaptedPage = adaptPageResult(data)
  return {
    ...adaptedPage,
    records: data.lists
  }
}

export function fetchGetPolicyDetail(id: number) {
  return http.get<Api.Policy.PolicyDetail>({
    url: `/policies/${id}`
  })
}

export function fetchAddPolicy(data: Api.Policy.PolicyAddParams) {
  return http.post<boolean>({
    url: '/policies',
    params: data
  })
}

export function fetchEditPolicy(id: number, data: Api.Policy.PolicyEditParams) {
  return http.put<boolean>({
    url: `/policies/${id}`,
    params: data
  })
}

export function fetchDeletePolicy(id: number) {
  return http.del<boolean>({
    url: `/policies/${id}`
  })
}

export function fetchGetPolicyVersions(policyId: number) {
  return http.get<Api.Policy.PolicyVersionHistory[]>({
    url: `/policies/${policyId}/versions`
  })
}
