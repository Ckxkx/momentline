import type {
  Appearance,
  ConflictResolution,
  DataSnapshot,
  DisplayPreferences,
  EventDraft,
  EventOrder,
  ImportPreview,
  ImportResult,
  Timeline,
  TimelineDraft,
  TimelineEvent,
  TimelineOrder
} from '../types'
import {
  createBackup,
  mergeImport,
  previewImport,
  sameEvent,
  sameTimelineFields,
  toIsoDate,
  validateEvent,
  validateEventDraft,
  validateTimeline,
  validateTimelineDraft,
  validateTimelines
} from './backup'

export const TIMELINES_KEY = 'momentline.timelines.v2'
export const PREFERENCES_KEY = 'momentline.preferences.v2'
const DIAGNOSTICS_KEY = 'momentline.diagnostics.v2'
const WRITE_LOCK_KEY = 'momentline.write-lock.v2'

const defaultPreferences: DisplayPreferences = {
  timelineOrder: 'createdDesc',
  eventOrders: {},
  appearance: 'system'
}

export class StoreError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'StoreError'
  }
}

export class ConcurrentModificationError extends Error {
  constructor(message = '数据已在其他页面发生变化') {
    super(message)
    this.name = 'ConcurrentModificationError'
  }
}

export class ImportPreviewExpiredError extends Error {
  readonly preview: ImportPreview
  readonly revision: number

  constructor(preview: ImportPreview, revision: number) {
    super('本地数据已变化，请确认更新后的导入预览')
    this.name = 'ImportPreviewExpiredError'
    this.preview = preview
    this.revision = revision
  }
}

export interface TimelineMutation {
  snapshot: DataSnapshot
  timeline: Timeline
}

export interface EventMutation {
  snapshot: DataSnapshot
  event: TimelineEvent
}

interface ImportMutation {
  snapshot: DataSnapshot
  result: ImportResult
}

interface LockRecord {
  token: string
  expiresAt: number
}

export class TimelineRepository {
  getSnapshot(): DataSnapshot {
    try {
      const text = localStorage.getItem(TIMELINES_KEY)
      if (text === null) return { timelines: [], revision: 0 }
      const parsed: unknown = JSON.parse(text)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('Invalid local snapshot')
      const record = parsed as Record<string, unknown>
      if (typeof record.revision !== 'number' || !Number.isInteger(record.revision) || record.revision < 0) {
        throw new Error('Invalid local revision')
      }
      return { timelines: validateTimelines(record.timelines), revision: record.revision }
    } catch (error) {
      this.recordDiagnostic('load', 'read', error)
      throw new StoreError('暂时无法读取时间线，请重试；原有数据未被清除')
    }
  }

  getPreferences(): DisplayPreferences {
    try {
      const text = localStorage.getItem(PREFERENCES_KEY)
      if (!text) return { ...defaultPreferences, eventOrders: {} }
      const value: unknown = JSON.parse(text)
      if (!value || typeof value !== 'object' || Array.isArray(value)) return { ...defaultPreferences, eventOrders: {} }
      const record = value as Record<string, unknown>
      const timelineOrders: TimelineOrder[] = ['createdDesc', 'createdAsc', 'titleAsc', 'titleDesc']
      const timelineOrder = timelineOrders.includes(record.timelineOrder as TimelineOrder)
        ? record.timelineOrder as TimelineOrder
        : 'createdDesc'
      const appearance: Appearance = record.appearance === 'light' || record.appearance === 'dark' ? record.appearance : 'system'
      const eventOrders: Record<string, EventOrder> = {}
      if (record.eventOrders && typeof record.eventOrders === 'object' && !Array.isArray(record.eventOrders)) {
        for (const [id, order] of Object.entries(record.eventOrders)) {
          if (order === 'newestFirst' || order === 'oldestFirst') eventOrders[id] = order
        }
      }
      return { timelineOrder, eventOrders, appearance }
    } catch {
      return { ...defaultPreferences, eventOrders: {} }
    }
  }

  async savePreferences(patch: Partial<DisplayPreferences> | ((current: DisplayPreferences) => Partial<DisplayPreferences>)): Promise<DisplayPreferences> {
    return this.withLock(() => {
      const current = this.getPreferences()
      const preferences = { ...current, ...(typeof patch === 'function' ? patch(current) : patch) }
      try {
        localStorage.setItem(PREFERENCES_KEY, JSON.stringify(preferences))
        return preferences
      } catch (error) {
        this.recordDiagnostic('preferences', 'write', error)
        throw new StoreError('显示偏好未能保存，请检查浏览器存储权限后重试')
      }
    })
  }

  async addTimeline(draft: TimelineDraft): Promise<TimelineMutation> {
    return this.withLock(() => {
      const current = this.getSnapshot()
      const normalized = validateTimelineDraft(draft)
      const now = toIsoDate(new Date())
      const timeline = validateTimeline({ ...normalized, id: crypto.randomUUID(), createdAt: now, events: [] })
      const snapshot = this.writeSnapshot(current, [...current.timelines, timeline], 'add-timeline')
      return { snapshot, timeline }
    })
  }

  async updateTimeline(original: Timeline, draft: TimelineDraft, force = false): Promise<TimelineMutation> {
    return this.withLock(() => {
      const current = this.getSnapshot()
      const index = current.timelines.findIndex(timeline => timeline.id === original.id)
      const latest = index >= 0 ? current.timelines[index] : undefined
      if (!latest) throw new StoreError('该时间线已被其他页面删除，本次修改未保存')
      if (!force && !sameTimelineFields(latest, original)) {
        throw new ConcurrentModificationError('该时间线已在其他页面被修改')
      }
      const normalized = validateTimelineDraft(draft)
      if (latest.title === normalized.title && latest.tone === normalized.tone) return { snapshot: current, timeline: latest }
      const timeline = validateTimeline({ ...latest, ...normalized, events: latest.events })
      const timelines = [...current.timelines]
      timelines[index] = timeline
      return { snapshot: this.writeSnapshot(current, timelines, 'update-timeline'), timeline }
    })
  }

  async deleteTimeline(id: string): Promise<DataSnapshot> {
    return this.withLock(() => {
      const current = this.getSnapshot()
      if (!current.timelines.some(timeline => timeline.id === id)) throw new StoreError('该时间线已在其他页面删除')
      return this.writeSnapshot(current, current.timelines.filter(timeline => timeline.id !== id), 'delete-timeline')
    })
  }

  async addEvent(timelineId: string, draft: EventDraft): Promise<EventMutation> {
    return this.withLock(() => {
      const current = this.getSnapshot()
      const index = current.timelines.findIndex(timeline => timeline.id === timelineId)
      if (index < 0) throw new StoreError('所属时间线已删除，本次事件修改未保存')
      const normalized = validateEventDraft(draft)
      const now = toIsoDate(new Date())
      const event = validateEvent({ ...normalized, id: crypto.randomUUID(), createdAt: now, updatedAt: now })
      const timeline = current.timelines[index]
      const timelines = [...current.timelines]
      timelines[index] = { ...timeline, events: [...timeline.events, event] }
      return { snapshot: this.writeSnapshot(current, timelines, 'add-event'), event }
    })
  }

  async updateEvent(original: TimelineEvent, originalTimeline: Timeline, draft: EventDraft, force = false): Promise<EventMutation> {
    return this.withLock(() => {
      const current = this.getSnapshot()
      const timelineIndex = current.timelines.findIndex(timeline => timeline.id === originalTimeline.id)
      if (timelineIndex < 0) throw new StoreError('所属时间线已删除，本次事件修改未保存')
      const timeline = current.timelines[timelineIndex]
      const eventIndex = timeline.events.findIndex(event => event.id === original.id)
      const latest = eventIndex >= 0 ? timeline.events[eventIndex] : undefined
      if (!latest) throw new StoreError('该事件已被其他页面删除，本次修改未保存')
      if (!force && (!sameTimelineFields(timeline, originalTimeline) || !sameEvent(latest, original))) {
        throw new ConcurrentModificationError('该事件或所属时间线已在其他页面被修改')
      }
      const normalized = validateEventDraft(draft)
      if (latest.title === normalized.title && latest.body === normalized.body
        && Date.parse(latest.occurredAt) === Date.parse(normalized.occurredAt)) {
        return { snapshot: current, event: latest }
      }
      const timestamp = new Date(Math.max(Date.now(), Date.parse(latest.createdAt)))
      const event = validateEvent({
        ...latest,
        ...normalized,
        createdAt: latest.createdAt,
        updatedAt: toIsoDate(timestamp)
      }, timelineIndex, eventIndex)
      const events = [...timeline.events]
      events[eventIndex] = event
      const timelines = [...current.timelines]
      timelines[timelineIndex] = { ...timeline, events }
      return { snapshot: this.writeSnapshot(current, timelines, 'update-event'), event }
    })
  }

  async deleteEvent(timelineId: string, eventId: string): Promise<DataSnapshot> {
    return this.withLock(() => {
      const current = this.getSnapshot()
      const timelineIndex = current.timelines.findIndex(timeline => timeline.id === timelineId)
      if (timelineIndex < 0) throw new StoreError('所属时间线已在其他页面删除')
      const timeline = current.timelines[timelineIndex]
      if (!timeline.events.some(event => event.id === eventId)) throw new StoreError('该事件已在其他页面删除')
      const timelines = [...current.timelines]
      timelines[timelineIndex] = { ...timeline, events: timeline.events.filter(event => event.id !== eventId) }
      return this.writeSnapshot(current, timelines, 'delete-event')
    })
  }

  previewBackup(text: string): { preview: ImportPreview; revision: number } {
    try {
      const current = this.getSnapshot()
      return { preview: previewImport(text, current.timelines), revision: current.revision }
    } catch (error) {
      this.recordDiagnostic('import', 'validate', error)
      throw error
    }
  }

  async importBackup(text: string, resolution: ConflictResolution | null, expectedRevision: number): Promise<ImportMutation> {
    return this.withLock(() => {
      const current = this.getSnapshot()
      let preview: ImportPreview
      try {
        preview = previewImport(text, current.timelines)
      } catch (error) {
        this.recordDiagnostic('import', 'validate', error)
        throw error
      }
      if (current.revision !== expectedRevision) throw new ImportPreviewExpiredError(preview, current.revision)
      const timelines = mergeImport(current.timelines, preview, resolution)
      const result: ImportResult = {
        timelines: {
          added: preview.timelineStats.added,
          skipped: preview.timelineStats.duplicates + (resolution === 'keepLocal' ? preview.timelineStats.conflicts : 0),
          replaced: resolution === 'useBackup' ? preview.timelineStats.conflicts : 0
        },
        events: {
          added: preview.eventStats.added,
          skipped: preview.eventStats.duplicates + (resolution === 'keepLocal' ? preview.eventStats.conflicts : 0),
          replaced: resolution === 'useBackup' ? preview.eventStats.conflicts : 0
        }
      }
      if (result.timelines.added + result.timelines.replaced + result.events.added + result.events.replaced === 0) {
        return { snapshot: current, result }
      }
      return { snapshot: this.writeSnapshot(current, timelines, 'import'), result }
    })
  }

  exportBackup(): { text: string; timelineCount: number; eventCount: number } {
    const current = this.getSnapshot()
    return {
      text: createBackup(current.timelines),
      timelineCount: current.timelines.length,
      eventCount: current.timelines.reduce((total, timeline) => total + timeline.events.length, 0)
    }
  }

  recordFailure(operation: string, stage: string, error: unknown): void {
    this.recordDiagnostic(operation, stage, error)
  }

  private writeSnapshot(current: DataSnapshot, timelines: Timeline[], operation: string): DataSnapshot {
    const snapshot = { timelines, revision: current.revision + 1 }
    try {
      localStorage.setItem(TIMELINES_KEY, JSON.stringify(snapshot))
      return snapshot
    } catch (error) {
      this.recordDiagnostic(operation, 'write', error)
      if (operation === 'import') throw new StoreError('导入未完成，本地数据已保持不变')
      if (operation.startsWith('delete')) throw new StoreError('删除未完成，本地数据已保持不变，请重试')
      throw new StoreError('暂时无法保存，请检查浏览器存储空间或站点数据权限后重试')
    }
  }

  private async withLock<T>(operation: () => T | Promise<T>): Promise<T> {
    if (typeof navigator !== 'undefined' && 'locks' in navigator) {
      return navigator.locks.request('momentline-local-write-v2', operation)
    }
    return this.withFallbackLock(operation)
  }

  private async withFallbackLock<T>(operation: () => T | Promise<T>): Promise<T> {
    const token = crypto.randomUUID()
    for (let attempt = 0; attempt < 24; attempt += 1) {
      try {
        const now = Date.now()
        const existing = this.readFallbackLock()
        if (!existing || existing.expiresAt < now) {
          localStorage.setItem(WRITE_LOCK_KEY, JSON.stringify({ token, expiresAt: now + 10000 }))
          await new Promise(resolve => setTimeout(resolve, 20 + Math.random() * 20))
          if (this.readFallbackLock()?.token === token) {
            try {
              return await operation()
            } finally {
              if (this.readFallbackLock()?.token === token) localStorage.removeItem(WRITE_LOCK_KEY)
            }
          }
        }
      } catch (error) {
        this.recordDiagnostic('lock', 'acquire', error)
        throw new StoreError('暂时无法保存，请检查浏览器站点数据权限后重试')
      }
      await new Promise(resolve => setTimeout(resolve, 35 + Math.random() * 30))
    }
    throw new StoreError('另一个页面正在保存，请稍后重试')
  }

  private readFallbackLock(): LockRecord | null {
    const text = localStorage.getItem(WRITE_LOCK_KEY)
    if (!text) return null
    try {
      const value = JSON.parse(text) as Partial<LockRecord>
      return typeof value.token === 'string' && typeof value.expiresAt === 'number' ? value as LockRecord : null
    } catch {
      return null
    }
  }

  private recordDiagnostic(operation: string, stage: string, error: unknown): void {
    try {
      const existing = JSON.parse(localStorage.getItem(DIAGNOSTICS_KEY) ?? '[]') as unknown
      const entries = Array.isArray(existing) ? existing.slice(-49) : []
      entries.push({
        timestamp: toIsoDate(new Date()),
        operation,
        stage,
        reason: error instanceof Error ? error.name : 'Error'
      })
      localStorage.setItem(DIAGNOSTICS_KEY, JSON.stringify(entries))
    } catch {
      return
    }
  }
}