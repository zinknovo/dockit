'use client'

import { useState, useRef, useEffect } from 'react'
import { http } from '@/lib/http'

interface PlanStep {
  toolName?: string
  status?: string
  description?: string
  observation?: string
}

interface AgentExecuteResponse {
  conversationId?: string
  answer?: string
  traceId?: string
  plan?: PlanStep[]
  toolResults?: Array<Record<string, unknown>>
}

interface Message {
  role: 'user' | 'agent'
  content: string
  traceId?: string
  plan?: PlanStep[]
  toolResults?: Array<Record<string, unknown>>
}

export default function AgentPage() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [conversationId, setConversationId] = useState('')
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages])

  const send = async () => {
    if (!input.trim() || loading) return
    const task = input.trim()
    setInput('')
    setLoading(true)

    setMessages(prev => [...prev, { role: 'user', content: task }])

    try {
      const data = await http.post<AgentExecuteResponse>({
        url: '/api/ai/agent/execute',
        data: {
          task,
          conversationId: conversationId || undefined,
          maxIterations: 3,
          maxToolCalls: 8,
          returnIntermediateSteps: true
        }
      })

      if (data.conversationId && !conversationId) {
        setConversationId(data.conversationId)
      }

      setMessages(prev => [...prev, {
        role: 'agent',
        content: data.answer || JSON.stringify(data),
        traceId: data.traceId,
        plan: data.plan,
        toolResults: data.toolResults
      }])
    } catch (err) {
      setMessages(prev => [...prev, {
        role: 'agent',
        content: '请求失败: ' + (err instanceof Error ? err.message : '未知错误')
      }])
    } finally {
      setLoading(false)
    }
  }

  const toggleExpand = (i: number) => {
    setExpanded(prev => ({ ...prev, [i]: !prev[i] }))
  }

  return (
    <div className="d-flex flex-column h-main">
      <div className="mb-4">
        <h2 className="text-xl fw-semibold text-g-900">AI Agent</h2>
        <p className="mt-1 text-sm text-g-600">智能规划执行 · 自动调用工具 · 多轮对话</p>
      </div>

      {/* 消息列表 */}
      <div className="flex-grow-1 overflow-auto space-y-4 pr-2">
        {messages.length === 0 && (
          <div className="text-center text-sm text-g-400 mt-5">
            <p className="text-lg mb-2">🤖</p>
            <p>输入任务，Agent 自动规划并执行</p>
            <p className="mt-1 text-xs">例如：&quot;帮我总结知识库里关于文档处理的资料&quot;</p>
          </div>
        )}

        {messages.map((msg, i) => (
          <div key={i} className={`d-flex ${msg.role === 'user' ? 'justify-content-end' : 'justify-content-start'}`}>
            <div className={`max-w-80p rounded-3 px-3 py-3 text-sm ${
              msg.role === 'user'
                ? 'bg-theme text-white'
                : 'bg-box border border-border text-g-800'
            }`}>
              <pre className="text-pre-wrap wrap-break-word font-sans">{msg.content}</pre>

              {msg.plan && msg.plan.length > 0 && (
                <button
                  onClick={() => toggleExpand(i)}
                  className="mt-2 text-xs text-g-400 hover-text-g-600"
                >
                  {expanded[i] ? '收起执行详情 ▲' : '查看执行详情 ▼'}
                </button>
              )}

              {expanded[i] && msg.plan && (
                <div className="mt-2 space-y-2 border-top border-g-100 pt-2">
                  <p className="text-xs fw-medium text-g-500">执行计划</p>
                  {msg.plan.map((step: PlanStep, si: number) => (
                    <div key={si} className="text-xs bg-g-50 rounded-2 p-2">
                      <span className="fw-medium">{step.toolName}</span>
                      <span className={`ml-2 px-1 rounded-1 text-white text-10px ${
                        step.status === 'success' ? 'bg-success' :
                        step.status === 'error' ? 'bg-danger' :
                        step.status === 'skipped' ? 'bg-secondary' : 'bg-warning'
                      }`}>{step.status || 'pending'}</span>
                      <p className="text-g-500 mt-1">{step.description}</p>
                      {step.observation && <p className="text-g-600 mt-1">→ {step.observation}</p>}
                    </div>
                  ))}
                  {msg.traceId && (
                    <p className="text-10px text-g-400">Trace ID: {msg.traceId}</p>
                  )}
                </div>
              )}
            </div>
          </div>
        ))}

        {loading && (
          <div className="d-flex justify-content-start">
            <div className="bg-box border border-border rounded-3 px-4 py-3">
              <p className="text-sm text-g-500 animate-pulse">Agent 思考中...</p>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* 输入框 */}
      <div className="input-group mt-3">
        <input
          className="form-control py-2"
          placeholder="输入任务描述..."
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && !e.shiftKey && send()}
          disabled={loading}
        />
        <button
          onClick={send}
          disabled={loading || !input.trim()}
          className="btn btn-primary fw-medium"
        >
          发送
        </button>
      </div>
    </div>
  )
}
