import type {
  ConflictResolution,
  EventDraft,
  ImportClassStats,
  ImportPreview,
  Timeline,
  TimelineDraft,
  TimelineEvent,
  TimelineOrder,
  EventOrder
} from '../types'

const datePattern = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?(Z|[+-]\d{2}:\d{2})$/
const tonePattern = /^#[0-9a-f]{6}$/i

export class BackupError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'BackupError'
  }
}

export function toIsoDate(date: Date): string {
  return date.toISOString().replace(/\.(\d{3})Z$/, (_match, milliseconds: string) => `.${milliseconds}000Z`)
}

export function normalizeDate(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const match = datePattern.exec(value)
  if (!match) return null
  const [, yearText, monthText, dayText, hourText, minuteText, secondText, , offset] = match
  const year = Number(yearText)
  const month = Number(monthText)
  const day = Number(dayText)
  const hour = Number(hourText)
  const minute = Number(minuteText)
  const second = Number(secondText)
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (month < 1 || month > 12 || day < 1 || day > days[month - 1]) return null
  if (hour > 23 || minute > 59 || second > 59) return null
  if (offset !== 'Z') {
    const offsetHour = Number(offset.slice(1, 3))
    const offsetMinute = Number(offset.slice(4, 6))
    if (offsetHour > 14 || offsetMinute > 59 || (offsetHour === 14 && offsetMinute !== 0)) return null
  }
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return null
  return toIsoDate(new Date(timestamp))
}

export function graphemeLength(value: string): number {
  if (typeof Intl.Segmenter === 'function') {
    const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' })
    return Array.from(segmenter.segment(value)).length
  }
  return Array.from(value).length
}

function requiredRecord(value: unknown, description: string): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new BackupError(`${description}格式不完整`)
  return value as Record<string, unknown>
}

function validId(value: unknown, description: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new BackupError(`${description}标识无效或缺失`)
  return value.trim()
}

export function validateTimelineDraft(value: unknown): TimelineDraft {
  const record = requiredRecord(value, '时间线')
  if (typeof record.title !== 'string') throw new BackupError('时间线名称缺失')
  const title = record.title.trim()
  if (!title) throw new BackupError('请输入时间线名称')
  if (graphemeLength(title) > 80) throw new BackupError('名称最多 80 个字符')
  if (typeof record.tone !== 'string' || !tonePattern.test(record.tone)) throw new BackupError('请选择有效的主题颜色')
  return { title, tone: record.tone.toUpperCase() }
}

export function validateEventDraft(value: unknown): EventDraft {
  const record = requiredRecord(value, '事件')
  if (typeof record.title !== 'string') throw new BackupError('事件标题缺失')
  const title = record.title.trim()
  if (!title) throw new BackupError('标题不能为空')
  if (graphemeLength(title) > 100) throw new BackupError('标题最多 100 个字符')
  if (typeof record.body !== 'string') throw new BackupError('备注字段缺失')
  if (graphemeLength(record.body) > 5000) throw new BackupError('备注最多 5000 个字符')
  const occurredAt = normalizeDate(record.occurredAt)
  if (!occurredAt) throw new BackupError('发生时间无效或缺少时区')
  return { title, body: record.body, occurredAt }
}

export function validateEvent(value: unknown, timelineIndex = 0, eventIndex = 0): TimelineEvent {
  const location = `第 ${timelineIndex + 1} 条时间线的第 ${eventIndex + 1} 条事件`
  try {
    const record = requiredRecord(value, location)
    const id = validId(record.id, `${location}事件`)
    const draft = validateEventDraft(record)
    const createdAt = normalizeDate(record.createdAt)
    if (!createdAt) throw new BackupError('创建时间无效或缺少时区')
    const updatedAt = normalizeDate(record.updatedAt)
    if (!updatedAt) throw new BackupError('更新时间无效或缺少时区')
    if (Date.parse(updatedAt) < Date.parse(createdAt)) throw new BackupError('更新时间不能早于创建时间')
    return { id, ...draft, createdAt, updatedAt }
  } catch (error) {
    if (error instanceof BackupError && error.message.startsWith(location)) throw error
    throw new BackupError(`${location}有误：${error instanceof Error ? error.message : '字段无效'}`)
  }
}

export function validateTimeline(value: unknown, index = 0): Timeline {
  const location = `第 ${index + 1} 条时间线`
  try {
    const record = requiredRecord(value, location)
    const id = validId(record.id, location)
    const draft = validateTimelineDraft(record)
    const createdAt = normalizeDate(record.createdAt)
    if (!createdAt) throw new BackupError('创建时间无效或缺少时区')
    if (!Array.isArray(record.events)) throw new BackupError('事件集合缺失或格式无效')
    const events = record.events.map((event, eventIndex) => validateEvent(event, index, eventIndex))
    const eventIds = new Set<string>()
    for (const event of events) {
      if (eventIds.has(event.id)) throw new BackupError(`事件标识“${event.id}”在所属时间线内重复`)
      eventIds.add(event.id)
    }
    return { id, ...draft, createdAt, events }
  } catch (error) {
    if (error instanceof BackupError && error.message.startsWith(location)) throw error
    throw new BackupError(`${location}有误：${error instanceof Error ? error.message : '字段无效'}`)
  }
}

export function validateTimelines(values: unknown): Timeline[] {
  if (!Array.isArray(values)) throw new BackupError('时间线集合缺失或格式无效')
  const timelines = values.map((value, index) => validateTimeline(value, index))
  const timelineIds = new Set<string>()
  for (const timeline of timelines) {
    if (timelineIds.has(timeline.id)) throw new BackupError(`时间线标识“${timeline.id}”在文件中重复`)
    timelineIds.add(timeline.id)
  }
  return timelines
}

export function sameTimelineFields(left: Timeline, right: Timeline): boolean {
  return left.id === right.id
    && left.title === right.title
    && left.tone === right.tone
    && Date.parse(left.createdAt) === Date.parse(right.createdAt)
}

export function sameEvent(left: TimelineEvent, right: TimelineEvent): boolean {
  return left.id === right.id
    && left.title === right.title
    && left.body === right.body
    && Date.parse(left.occurredAt) === Date.parse(right.occurredAt)
    && Date.parse(left.createdAt) === Date.parse(right.createdAt)
    && Date.parse(left.updatedAt) === Date.parse(right.updatedAt)
}

function compareCodePoints(left: string, right: string): number {
  const leftPoints = Array.from(left)
  const rightPoints = Array.from(right)
  const length = Math.min(leftPoints.length, rightPoints.length)
  for (let index = 0; index < length; index += 1) {
    const difference = (leftPoints[index].codePointAt(0) ?? 0) - (rightPoints[index].codePointAt(0) ?? 0)
    if (difference !== 0) return difference
  }
  return leftPoints.length - rightPoints.length
}

export function sortTimelines(timelines: Timeline[], order: TimelineOrder): Timeline[] {
  return [...timelines].sort((left, right) => {
    if (order === 'titleAsc' || order === 'titleDesc') {
      const titleDifference = compareCodePoints(left.title.toLowerCase(), right.title.toLowerCase())
      if (titleDifference !== 0) return order === 'titleAsc' ? titleDifference : -titleDifference
      const createdDifference = Date.parse(right.createdAt) - Date.parse(left.createdAt)
      if (createdDifference !== 0) return createdDifference
      return compareCodePoints(left.id, right.id)
    }
    const createdDifference = Date.parse(left.createdAt) - Date.parse(right.createdAt)
    if (createdDifference !== 0) return order === 'createdAsc' ? createdDifference : -createdDifference
    return compareCodePoints(left.id, right.id)
  })
}

export function sortEvents(events: TimelineEvent[], order: EventOrder): TimelineEvent[] {
  return [...events].sort((left, right) => {
    const occurredDifference = Date.parse(left.occurredAt) - Date.parse(right.occurredAt)
    if (occurredDifference !== 0) return order === 'newestFirst' ? -occurredDifference : occurredDifference
    const updatedDifference = Date.parse(right.updatedAt) - Date.parse(left.updatedAt)
    if (updatedDifference !== 0) return updatedDifference
    return compareCodePoints(left.id, right.id)
  })
}

export function createBackup(timelines: Timeline[], exportedAt = new Date()): string {
  return JSON.stringify({
    format: 'MomentLine',
    version: 2,
    exportedAt: toIsoDate(exportedAt),
    timelines
  }, null, 2)
}

export function decodeBackup(text: string): Timeline[] {
  let root: unknown
  try {
    root = JSON.parse(text)
  } catch {
    throw new BackupError('这不是有效的 Moment Line 备份文件')
  }
  if (!root || typeof root !== 'object' || Array.isArray(root)) {
    throw new BackupError('这不是有效的 Moment Line 备份文件')
  }
  const record = root as Record<string, unknown>
  if (record.format !== 'MomentLine' || typeof record.version !== 'number' || !Number.isInteger(record.version)
    || !normalizeDate(record.exportedAt) || !Array.isArray(record.timelines)) {
    throw new BackupError('这不是有效的 Moment Line 备份文件')
  }
  if (record.version !== 2) {
    throw new BackupError('该备份版本暂不受支持，请更新应用或使用兼容备份')
  }
  return validateTimelines(record.timelines)
}

function emptyStats(): ImportClassStats {
  return { added: 0, duplicates: 0, conflicts: 0, invalid: 0 }
}

export function previewImport(text: string, localTimelines: Timeline[]): ImportPreview {
  const imported = decodeBackup(text)
  const localById = new Map(localTimelines.map(timeline => [timeline.id, timeline]))
  const preview: ImportPreview = {
    timelineStats: emptyStats(),
    eventStats: emptyStats(),
    timelineAdditions: [],
    timelineDuplicates: [],
    timelineConflicts: [],
    eventAdditions: [],
    eventDuplicates: [],
    eventConflicts: []
  }

  for (const timeline of imported) {
    const localTimeline = localById.get(timeline.id)
    if (!localTimeline) {
      preview.timelineAdditions.push(timeline)
      preview.timelineStats.added += 1
    } else if (sameTimelineFields(localTimeline, timeline)) {
      preview.timelineDuplicates.push(timeline)
      preview.timelineStats.duplicates += 1
    } else {
      preview.timelineConflicts.push(timeline)
      preview.timelineStats.conflicts += 1
    }

    const localEvents = new Map((localTimeline?.events ?? []).map(event => [event.id, event]))
    for (const event of timeline.events) {
      const localEvent = localEvents.get(event.id)
      const item = { timelineId: timeline.id, event }
      if (!localEvent) {
        preview.eventAdditions.push(item)
        preview.eventStats.added += 1
      } else if (sameEvent(localEvent, event)) {
        preview.eventDuplicates.push(item)
        preview.eventStats.duplicates += 1
      } else {
        preview.eventConflicts.push(item)
        preview.eventStats.conflicts += 1
      }
    }
  }
  return preview
}

export function mergeImport(localTimelines: Timeline[], preview: ImportPreview, resolution: ConflictResolution | null): Timeline[] {
  const conflictCount = preview.timelineStats.conflicts + preview.eventStats.conflicts
  if (conflictCount > 0 && resolution === null) throw new BackupError('请选择冲突处理方式')
  const result = localTimelines.map(timeline => ({ ...timeline, events: [...timeline.events] }))
  const resultById = new Map(result.map(timeline => [timeline.id, timeline]))

  if (resolution === 'useBackup') {
    for (const imported of preview.timelineConflicts) {
      const local = resultById.get(imported.id)
      if (local) {
        local.title = imported.title
        local.tone = imported.tone
        local.createdAt = imported.createdAt
      }
    }
    for (const { timelineId, event } of preview.eventConflicts) {
      const timeline = resultById.get(timelineId)
      const index = timeline?.events.findIndex(item => item.id === event.id) ?? -1
      if (timeline && index >= 0) timeline.events[index] = { ...event }
    }
  }

  for (const { timelineId, event } of preview.eventAdditions) {
    const timeline = resultById.get(timelineId)
    if (timeline) timeline.events.push({ ...event })
  }
  for (const imported of preview.timelineAdditions) {
    result.push({ ...imported, events: imported.events.map(event => ({ ...event })) })
  }
  return result
}