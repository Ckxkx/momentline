import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type {
  Appearance,
  ConflictResolution,
  DataSnapshot,
  DisplayPreferences,
  EventDraft,
  EventOrder,
  MomentNotice,
  Timeline,
  TimelineDraft,
  TimelineEvent,
  TimelineOrder
} from '../types'
import { PREFERENCES_KEY, TIMELINES_KEY, TimelineRepository } from '../lib/store'

interface Snapshot extends DataSnapshot {
  loadError: string | null
}

function loadSnapshot(repository: TimelineRepository): Snapshot {
  try {
    return { ...repository.getSnapshot(), loadError: null }
  } catch (error) {
    return { timelines: [], revision: 0, loadError: error instanceof Error ? error.message : '暂时无法读取时间线' }
  }
}

export function useTimelineStore() {
  const repository = useMemo(() => new TimelineRepository(), [])
  const [snapshot, setSnapshot] = useState<Snapshot>(() => loadSnapshot(repository))
  const [preferences, setPreferences] = useState<DisplayPreferences>(() => repository.getPreferences())
  const [notice, setNotice] = useState<MomentNotice | null>(null)
  const noticeId = useRef(0)

  const notify = useCallback((message: string) => {
    noticeId.current += 1
    setNotice({ id: noticeId.current, message })
  }, [])

  const reload = useCallback(() => {
    setSnapshot(loadSnapshot(repository))
    setPreferences(repository.getPreferences())
  }, [repository])

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === TIMELINES_KEY || event.key === null) setSnapshot(loadSnapshot(repository))
      if (event.key === PREFERENCES_KEY || event.key === null) setPreferences(repository.getPreferences())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [repository])

  useEffect(() => {
    document.documentElement.dataset.theme = preferences.appearance
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const updateThemeColor = () => {
      const dark = preferences.appearance === 'dark' || (preferences.appearance === 'system' && media.matches)
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#171b19' : '#f5f4ef')
    }
    updateThemeColor()
    media.addEventListener('change', updateThemeColor)
    return () => media.removeEventListener('change', updateThemeColor)
  }, [preferences.appearance])

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(current => current?.id === notice.id ? null : current), 3600)
    return () => window.clearTimeout(timer)
  }, [notice])

  const savePreferences = useCallback((patch: (current: DisplayPreferences) => Partial<DisplayPreferences>) => {
    void repository.savePreferences(patch).then(setPreferences).catch(error => {
      notify(error instanceof Error ? error.message : '显示偏好未能保存')
    })
  }, [notify, repository])

  const setTimelineOrder = useCallback((timelineOrder: TimelineOrder) => {
    savePreferences(() => ({ timelineOrder }))
  }, [savePreferences])

  const setEventOrder = useCallback((timelineId: string, eventOrder: EventOrder) => {
    savePreferences(current => ({ eventOrders: { ...current.eventOrders, [timelineId]: eventOrder } }))
  }, [savePreferences])

  const setAppearance = useCallback((appearance: Appearance) => {
    savePreferences(() => ({ appearance }))
  }, [savePreferences])

  const addTimeline = useCallback(async (draft: TimelineDraft) => {
    const result = await repository.addTimeline(draft)
    setSnapshot({ ...result.snapshot, loadError: null })
    notify('时间线已创建')
    return result.timeline
  }, [notify, repository])

  const updateTimeline = useCallback(async (original: Timeline, draft: TimelineDraft, force = false) => {
    const result = await repository.updateTimeline(original, draft, force)
    setSnapshot({ ...result.snapshot, loadError: null })
    notify('时间线已更新')
    return result.timeline
  }, [notify, repository])

  const deleteTimeline = useCallback(async (id: string) => {
    const next = await repository.deleteTimeline(id)
    setSnapshot({ ...next, loadError: null })
    notify('时间线及其事件已删除')
  }, [notify, repository])

  const addEvent = useCallback(async (timelineId: string, draft: EventDraft) => {
    const result = await repository.addEvent(timelineId, draft)
    setSnapshot({ ...result.snapshot, loadError: null })
    notify('事件已保存')
    return result.event
  }, [notify, repository])

  const updateEvent = useCallback(async (original: TimelineEvent, timeline: Timeline, draft: EventDraft, force = false) => {
    const result = await repository.updateEvent(original, timeline, draft, force)
    setSnapshot({ ...result.snapshot, loadError: null })
    notify('事件已更新')
    return result.event
  }, [notify, repository])

  const deleteEvent = useCallback(async (timelineId: string, eventId: string) => {
    const next = await repository.deleteEvent(timelineId, eventId)
    setSnapshot({ ...next, loadError: null })
    notify('事件已删除')
  }, [notify, repository])

  const previewBackup = useCallback((text: string) => repository.previewBackup(text), [repository])

  const importBackup = useCallback(async (text: string, resolution: ConflictResolution | null, revision: number) => {
    const result = await repository.importBackup(text, resolution, revision)
    setSnapshot({ ...result.snapshot, loadError: null })
    notify('完整备份已导入')
    return result.result
  }, [notify, repository])

  return {
    ...snapshot,
    ...preferences,
    notice,
    repository,
    reload,
    setTimelineOrder,
    setEventOrder,
    setAppearance,
    addTimeline,
    updateTimeline,
    deleteTimeline,
    addEvent,
    updateEvent,
    deleteEvent,
    previewBackup,
    importBackup
  }
}

export type TimelineStoreController = ReturnType<typeof useTimelineStore>