'use client'

import { useState, useRef, useEffect } from 'react'
import { http } from '@/lib/http'

interface Message {
  role: 'user' | 'agent'
  content: string
  traceId?: string
  plan?: any[]
  toolResults?: any[]
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
      const data = await http.post<any>({
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
    } catch (err: any) {
      setMessages(prev => [...prev, {
        role: 'agent',
        content: '请求失败: ' + (err.message || '未知错误')
      }])
    } finally {
      setLoading(false)
    }
  }

  const toggleExpand = (i: number) => {
    setExpanded(prev => ({ ...prev, [i]: !prev[i] }))
  }

  return (
    <div className="flex flex-col h-[calc(100vh-8rem)]">
      <div className="mb-4">
        <h2 className="text-xl font-semibold text-g-900">AI Agent</h2>
        <p className="mt-1 text-sm text-g-600">智能规划执行 · 自动调用工具 · 多轮对话</p>
      </div>

      {/* 消息列表 */}
      <div className="flex-1 overflow-y-auto space-y-4 pr-2">
        {messages.length === 0 && (
          <div className="text-center text-sm text-g-400 mt-20">
            <p className="text-lg mb-2">🤖</p>
            <p>输入任务，Agent 自动规划并执行</p>
            <p className="mt-1 text-xs">例如：&quot;帮我总结知识库里关于文档处理的资料&quot;</p>
          </div>
        )}

        {messages.map((msg, i) => (
          <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[80%] rounded-2xl px-4 py-3 text-sm ${
              msg.role === 'user'
                ? 'bg-theme text-white'
                : 'bg-[var(--default-box-color)] border border-[var(--default-border)] text-g-800'
            }`}>
              <pre className="whitespace-pre-wrap break-words font-sans">{msg.content}</pre>

              {msg.plan && msg.plan.length > 0 && (
                <button
                  onClick={() => toggleExpand(i)}
                  className="mt-2 text-xs text-g-400 hover:text-g-600"
                >
                  {expanded[i] ? '收起执行详情 ▲' : '查看执行详情 ▼'}
                </button>
              )}

              {expanded[i] && msg.plan && (
                <div className="mt-2 space-y-2 border-t border-g-100 pt-2">
                  <p className="text-xs font-medium text-g-500">执行计划</p>
                  {msg.plan.map((step: any, si: number) => (
                    <div key={si} className="text-xs bg-g-50 rounded-lg p-2">
                      <span className="font-medium">{step.toolName}</span>
                      <span className={`ml-2 px-1 rounded text-white text-[10px] ${
                        step.status === 'success' ? 'bg-green-500' :
                        step.status === 'error' ? 'bg-red-500' :
                        step.status === 'skipped' ? 'bg-gray-400' : 'bg-yellow-500'
                      }`}>{step.status || 'pending'}</span>
                      <p className="text-g-500 mt-0.5">{step.description}</p>
                      {step.observation && <p className="text-g-600 mt-0.5">→ {step.observation}</p>}
                    </div>
                  ))}
                  {msg.traceId && (
                    <p className="text-[10px] text-g-400">Trace ID: {msg.traceId}</p>
                  )}
                </div>
              )}
            </div>
          </div>
        ))}

        {loading && (
          <div className="flex justify-start">
            <div className="bg-[var(--default-box-color)] border border-[var(--default-border)] rounded-2xl px-4 py-3">
              <p className="text-sm text-g-500 animate-pulse">Agent 思考中...</p>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* 输入框 */}
      <div className="mt-4 flex gap-2">
        <input
          className="flex-1 rounded-xl border border-[var(--default-border)] bg-[var(--default-box-color)] px-4 py-3 text-sm focus:border-theme focus:outline-none"
          placeholder="输入任务描述..."
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && !e.shiftKey && send()}
          disabled={loading}
        />
        <button
          onClick={send}
          disabled={loading || !input.trim()}
          className="rounded-xl bg-theme px-6 py-3 text-sm font-medium text-white transition hover:opacity-90 disabled:opacity-50"
        >
          发送
        </button>
      </div>
    </div>
  )
}
