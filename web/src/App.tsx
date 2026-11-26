import { useEffect, useState, useSyncExternalStore } from 'react'
import type { Timeline, TimelineEvent } from './types'
import { useTimelineStore } from './hooks/useTimelineStore'
import { getPwaStatus, subscribeToPwaStatus } from './lib/pwa'
import { TimelineList } from './components/TimelineList'
import { EventTimeline } from './components/EventTimeline'
import { TimelineEditor } from './components/TimelineEditor'
import { EventEditor } from './components/EventEditor'
import { EventDetail } from './components/EventDetail'
import { DataDialog } from './components/DataDialog'
import { ConfirmDialog } from './components/ConfirmDialog'
import { Icon } from './components/Icons'

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

type EditorState =
  | { kind: 'timeline'; timeline: Timeline | null }
  | { kind: 'event'; timeline: Timeline; event: TimelineEvent | null }
  | undefined

type DeleteTarget =
  | { kind: 'timeline'; timeline: Timeline }
  | { kind: 'event'; timelineId: string; event: TimelineEvent }
  | null

export default function App() {
  const store = useTimelineStore()
  const pwa = useSyncExternalStore(subscribeToPwaStatus, getPwaStatus)
  const [selectedTimelineId, setSelectedTimelineId] = useState<string | null>(null)
  const [editor, setEditor] = useState<EditorState>(undefined)
  const [detailTarget, setDetailTarget] = useState<{ timelineId: string; eventId: string } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget>(null)
  const [showingData, setShowingData] = useState(false)
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null)

  const selectedTimeline = selectedTimelineId ? store.timelines.find(timeline => timeline.id === selectedTimelineId) : undefined
  const detailTimeline = detailTarget ? store.timelines.find(timeline => timeline.id === detailTarget.timelineId) : undefined
  const detailEvent = detailTimeline?.events.find(event => event.id === detailTarget?.eventId)
  const totalEvents = store.timelines.reduce((total, timeline) => total + timeline.events.length, 0)

  useEffect(() => {
    const beforeInstall = (event: Event) => {
      event.preventDefault()
      setInstallPrompt(event as BeforeInstallPromptEvent)
    }
    const installed = () => setInstallPrompt(null)
    window.addEventListener('beforeinstallprompt', beforeInstall)
    window.addEventListener('appinstalled', installed)
    return () => {
      window.removeEventListener('beforeinstallprompt', beforeInstall)
      window.removeEventListener('appinstalled', installed)
    }
  }, [])

  const install = async () => {
    if (!installPrompt) return
    await installPrompt.prompt()
    await installPrompt.userChoice
    setInstallPrompt(null)
  }

  const confirmDelete = async () => {
    if (!deleteTarget) return
    if (deleteTarget.kind === 'timeline') {
      await store.deleteTimeline(deleteTarget.timeline.id)
      if (selectedTimelineId === deleteTarget.timeline.id) setSelectedTimelineId(null)
    } else {
      await store.deleteEvent(deleteTarget.timelineId, deleteTarget.event.id)
      if (detailTarget?.eventId === deleteTarget.event.id) setDetailTarget(null)
    }
    setDeleteTarget(null)
  }

  return <div className="app-shell">
    <header className="app-header">
      <button className="brand" onClick={() => setSelectedTimelineId(null)} aria-label="Moment Line 首页">
        <span className="brand-symbol" aria-hidden="true"><i /><i /><i /></span>
        <span>Moment Line</span>
      </button>
      <div className="header-actions">
        <span className={`connectivity-dot ${pwa.online ? '' : 'is-offline'}`} title={pwa.online ? '本地数据已可用' : '当前离线，更改仍会保存在本地'} aria-label={pwa.online ? '在线' : '离线'} />
        <button className="icon-button" onClick={() => setShowingData(true)} aria-label="设置与数据"><Icon name="settings" /></button>
        {(!selectedTimelineId || selectedTimeline) && <button className="button button-primary add-button" onClick={() => {
          if (selectedTimeline) setEditor({ kind: 'event', timeline: selectedTimeline, event: null })
          else setEditor({ kind: 'timeline', timeline: null })
        }}>
          <Icon name="plus" /><span>{selectedTimeline ? '记录事件' : '新建时间线'}</span>
        </button>}
      </div>
    </header>

    <main className="main-content">
      {store.loadError ? <section className="empty-state">
        <span className="empty-symbol">!</span>
        <h2>暂时无法读取本地数据</h2>
        <p>{store.loadError}</p>
        <button className="button button-primary" onClick={store.reload}>重试</button>
      </section> : selectedTimelineId ? selectedTimeline ? <EventTimeline
        key={selectedTimeline.id}
        timeline={selectedTimeline}
        order={store.eventOrders[selectedTimeline.id] ?? 'newestFirst'}
        onOrderChange={order => store.setEventOrder(selectedTimeline.id, order)}
        onBack={() => setSelectedTimelineId(null)}
        onAdd={() => setEditor({ kind: 'event', timeline: selectedTimeline, event: null })}
        onEditTimeline={() => setEditor({ kind: 'timeline', timeline: selectedTimeline })}
        onOpen={event => setDetailTarget({ timelineId: selectedTimeline.id, eventId: event.id })}
        onEdit={event => setEditor({ kind: 'event', timeline: selectedTimeline, event })}
        onDelete={event => setDeleteTarget({ kind: 'event', timelineId: selectedTimeline.id, event })}
      /> : <section className="empty-state">
        <h2>时间线已不存在</h2>
        <p>它可能在其他标签页中被删除。返回列表查看最新本地数据。</p>
        <button className="button button-primary" onClick={() => setSelectedTimelineId(null)}>返回时间线列表</button>
      </section> : <>
        <div className="page-heading">
          <h1>时间线</h1>
          <p>{store.timelines.length} 条时间线 · {totalEvents} 条事件</p>
        </div>
        <TimelineList
          timelines={store.timelines}
          order={store.timelineOrder}
          onOrderChange={store.setTimelineOrder}
          onAdd={() => setEditor({ kind: 'timeline', timeline: null })}
          onOpen={timeline => setSelectedTimelineId(timeline.id)}
          onEdit={timeline => setEditor({ kind: 'timeline', timeline })}
          onDelete={timeline => setDeleteTarget({ kind: 'timeline', timeline })}
          onOpenData={() => setShowingData(true)}
        />
      </>}
    </main>

    <footer className="app-footer"><span className="brand-symbol small" aria-hidden="true"><i /><i /><i /></span>你的故事只属于你。无需账号，不上传服务器。</footer>

    {editor?.kind === 'timeline' && <TimelineEditor timeline={editor.timeline} store={store} onClose={() => setEditor(undefined)} />}
    {editor?.kind === 'event' && <EventEditor event={editor.event} timeline={editor.timeline} store={store} onClose={() => setEditor(undefined)} />}
    {detailTimeline && detailEvent && <EventDetail event={detailEvent} timeline={detailTimeline} onClose={() => setDetailTarget(null)} onEdit={() => {
      setEditor({ kind: 'event', timeline: detailTimeline, event: detailEvent })
      setDetailTarget(null)
    }} />}
    {showingData && <DataDialog store={store} onClose={() => setShowingData(false)} canInstall={installPrompt !== null} onInstall={install} online={pwa.online} offlineReady={pwa.offlineReady} preparationError={pwa.preparationError} />}
    {deleteTarget && <ConfirmDialog
      title={deleteTarget.kind === 'timeline' ? '删除时间线' : '删除事件'}
      description={deleteTarget.kind === 'timeline'
        ? `确定删除“${deleteTarget.timeline.title}”及其全部 ${deleteTarget.timeline.events.length} 条事件吗？此操作无法撤销。`
        : `确定删除事件“${deleteTarget.event.title}”吗？同时间线的其他事件不会受影响。`}
      confirmLabel={deleteTarget.kind === 'timeline' ? '删除时间线及事件' : '删除事件'}
      onClose={() => setDeleteTarget(null)}
      onConfirm={confirmDelete}
    />}
    {store.notice && <div className="toast" role="status" key={store.notice.id}>{store.notice.message}</div>}
  </div>
}