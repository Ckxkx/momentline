import { toIsoDate } from './backup'

const fullDateFormatter = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'full' })
const dateFormatter = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
const timeFormatter = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false
})

export function formatFullDate(value: string | Date): string {
  return fullDateFormatter.format(typeof value === 'string' ? new Date(value) : value)
}

export function formatDate(value: string | Date): string {
  return dateFormatter.format(typeof value === 'string' ? new Date(value) : value)
}

export function formatTime(value: string | Date): string {
  return timeFormatter.format(typeof value === 'string' ? new Date(value) : value)
}

export function formatDateTime(value: string | Date): string {
  return dateTimeFormatter.format(typeof value === 'string' ? new Date(value) : value)
}

export function toLocalInputValue(isoDate?: string): string {
  const date = isoDate ? new Date(isoDate) : new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function fromLocalInputValue(value: string): string {
  if (!value) throw new Error('请选择有效的发生时间')
  const timestamp = new Date(value)
  if (Number.isNaN(timestamp.getTime())) throw new Error('请选择有效的发生时间')
  return toIsoDate(timestamp)
}