export interface TimelineEvent {
  id: string
  title: string
  body: string
  occurredAt: string
  createdAt: string
  updatedAt: string
}

export interface Timeline {
  id: string
  title: string
  tone: string
  createdAt: string
  events: TimelineEvent[]
}

export interface TimelineDraft {
  title: string
  tone: string
}

export interface EventDraft {
  title: string
  body: string
  occurredAt: string
}

export type TimelineOrder = 'createdDesc' | 'createdAsc' | 'titleAsc' | 'titleDesc'
export type EventOrder = 'newestFirst' | 'oldestFirst'
export type Appearance = 'system' | 'light' | 'dark'
export type ConflictResolution = 'keepLocal' | 'useBackup'

export interface DisplayPreferences {
  timelineOrder: TimelineOrder
  eventOrders: Record<string, EventOrder>
  appearance: Appearance
}

export interface ImportClassStats {
  added: number
  duplicates: number
  conflicts: number
  invalid: number
}

export interface ImportedEvent {
  timelineId: string
  event: TimelineEvent
}

export interface ImportPreview {
  timelineStats: ImportClassStats
  eventStats: ImportClassStats
  timelineAdditions: Timeline[]
  timelineDuplicates: Timeline[]
  timelineConflicts: Timeline[]
  eventAdditions: ImportedEvent[]
  eventDuplicates: ImportedEvent[]
  eventConflicts: ImportedEvent[]
}

export interface ImportCounts {
  added: number
  skipped: number
  replaced: number
}

export interface ImportResult {
  timelines: ImportCounts
  events: ImportCounts
}

export interface MomentNotice {
  id: number
  message: string
}

export interface DataSnapshot {
  timelines: Timeline[]
  revision: number
}