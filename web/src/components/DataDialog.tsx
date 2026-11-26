import { useRef, useState } from 'react'
import type { DragEvent } from 'react'
import type { ConflictResolution, ImportClassStats, ImportPreview } from '../types'
import type { TimelineStoreController } from '../hooks/useTimelineStore'
import { ImportPreviewExpiredError } from '../lib/store'
import { retryOfflinePreparation } from '../lib/pwa'
import { Icon } from './Icons'
import { Modal } from './Modal'

interface DataDialogProps {
  store: TimelineStoreController
  onClose: () => void
  canInstall: boolean
  onInstall: () => Promise<void>
  online: boolean
  offlineReady: boolean
  preparationError: boolean
}

function backupFilename(): string {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `MomentLine-${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}.json`
}

function PreviewTable({ title, stats }: { title: string; stats: ImportClassStats }) {
  return <div className="preview-table">
    <h4>{title}</h4>
    <div className="preview-stats">
      <span><strong>{stats.added}</strong>新增</span>
      <span><strong>{stats.duplicates}</strong>重复</span>
      <span><strong>{stats.conflicts}</strong>冲突</span>
      <span><strong>{stats.invalid}</strong>无效</span>
    </div>
  </div>
}

export function DataDialog({ store, onClose, canInstall, onInstall, online, offlineReady, preparationError }: DataDialogProps) {
  const [confirmingExport, setConfirmingExport] = useState(false)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [previewRevision, setPreviewRevision] = useState(0)
  const [backupText, setBackupText] = useState('')
  const [sourceName, setSourceName] = useState('')
  const [resolution, setResolution] = useState<ConflictResolution | null>(null)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [busy, setBusy] = useState(false)
  const [offlineBusy, setOfflineBusy] = useState(false)
  const [dragging, setDragging] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)
  const timelineCount = store.timelines.length
  const eventCount = store.timelines.reduce((total, timeline) => total + timeline.events.length, 0)
  const conflictCount = (preview?.timelineStats.conflicts ?? 0) + (preview?.eventStats.conflicts ?? 0)

  const exportData = () => {
    setError('')
    setStatus('')
    try {
      const backup = store.repository.exportBackup()
      const filename = backupFilename()
      const url = URL.createObjectURL(new Blob([backup.text], { type: 'application/json;charset=utf-8' }))
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
      setStatus(`${filename} 已交付浏览器下载，包含 ${backup.timelineCount} 条时间线、${backup.eventCount} 条事件。`)
      setConfirmingExport(false)
    } catch (caught) {
      store.repository.recordFailure('export', 'download', caught)
      setError(caught instanceof Error ? caught.message : '备份文件未能导出，请允许浏览器下载后重试')
    }
  }

  const readFile = async (file: File) => {
    setError('')
    setStatus('')
    setBusy(true)
    try {
      const bytes = await file.arrayBuffer()
      const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
      const result = store.previewBackup(text)
      setBackupText(text)
      setSourceName(file.name)
      setPreview(result.preview)
      setPreviewRevision(result.revision)
      setResolution(null)
    } catch (caught) {
      store.repository.recordFailure('import', 'read', caught)
      setPreview(null)
      setBackupText('')
      setError(caught instanceof Error ? caught.message : '无法读取所选文件，请重新选择备份文件')
    } finally {
      setBusy(false)
    }
  }

  const dropFile = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    setDragging(false)
    const file = event.dataTransfer.files[0]
    if (file) void readFile(file)
  }

  const cancelImport = () => {
    setPreview(null)
    setBackupText('')
    setSourceName('')
    setResolution(null)
    setError('')
  }

  const confirmImport = async () => {
    if (!backupText || !preview) return
    setBusy(true)
    setError('')
    try {
      const result = await store.importBackup(backupText, resolution, previewRevision)
      setPreview(null)
      setBackupText('')
      setSourceName('')
      setStatus(`导入完成。时间线：新增 ${result.timelines.added}、跳过 ${result.timelines.skipped}、替换 ${result.timelines.replaced}；事件：新增 ${result.events.added}、跳过 ${result.events.skipped}、替换 ${result.events.replaced}。`)
    } catch (caught) {
      if (caught instanceof ImportPreviewExpiredError) {
        setPreview(caught.preview)
        setPreviewRevision(caught.revision)
        setResolution(null)
      }
      setError(caught instanceof Error ? caught.message : '导入未完成，本地数据已保持不变')
    } finally {
      setBusy(false)
    }
  }

  const retryOffline = async () => {
    setOfflineBusy(true)
    setError('')
    try {
      await retryOfflinePreparation()
      setStatus('离线资源已准备完成，可在无网络时重新打开。')
    } catch {
      setError('离线准备未完成，请联网后重试；本地数据不会因此删除')
    } finally {
      setOfflineBusy(false)
    }
  }

  return <Modal title="设置与数据" onClose={onClose} wide>
    <section className="settings-section">
      <h3>外观</h3>
      <div className="segmented-control" aria-label="显示模式">
        {([['system', '跟随系统'], ['light', '浅色'], ['dark', '深色']] as const).map(([value, label]) =>
          <button key={value} className={store.appearance === value ? 'selected' : ''} onClick={() => store.setAppearance(value)} aria-pressed={store.appearance === value}>{label}</button>
        )}
      </div>
    </section>

    <section className="settings-section offline-section">
      <div>
        <h3>离线使用</h3>
        <p className={`offline-status ${offlineReady ? 'ready' : ''}`}>
          <Icon name={offlineReady ? 'check' : 'offline'} />
          {offlineReady ? '离线已就绪' : preparationError ? '离线准备未完成' : '正在准备离线资源'}
          <span>· 当前{online ? '在线' : '离线'}</span>
        </p>
      </div>
      {!offlineReady && <button className="button button-secondary" onClick={() => void retryOffline()} disabled={offlineBusy}><Icon name="refresh" />{offlineBusy ? '重试中…' : '重试'}</button>}
    </section>

    {canInstall && <section className="settings-section install-section">
      <div>
        <h3>安装 Moment Line</h3>
        <p>以独立窗口打开，与同源浏览器共享同一份本地数据。</p>
      </div>
      <button className="button button-secondary" onClick={() => void onInstall()}><Icon name="install" />安装应用</button>
    </section>}

    <section className="settings-section">
      <h3>完整备份 <span>{timelineCount} 条时间线 · {eventCount} 条事件</span></h3>
      <p className="risk-note">数据仅留在当前浏览器。公共设备使用、清除站点数据、临时浏览会话或卸载应用都可能导致数据丢失，请定期导出完整备份。</p>
      {!confirmingExport ? <button className="button button-primary" onClick={() => { setConfirmingExport(true); setError(''); setStatus('') }}><Icon name="download" />导出全部数据</button> : <div className="confirm-panel">
        <strong>妥善保管备份</strong>
        <p>备份包含全部时间线、事件标题和备注等个人内容，请仅保存到你信任的位置。</p>
        <div className="form-actions">
          <button className="button button-secondary" onClick={() => setConfirmingExport(false)}>取消</button>
          <button className="button button-primary" onClick={exportData}>继续导出</button>
        </div>
      </div>}
    </section>

    <section className="settings-section import-section">
      <h3>从备份导入</h3>
      <p>只会合入新增内容和确认的冲突，不会删除备份中未出现的本地数据。</p>
      {!preview && <div
        className={`drop-zone ${dragging ? 'dragging' : ''}`}
        onDragOver={event => { event.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={dropFile}
      >
        <Icon name="upload" />
        <p>拖放 Moment Line v2 JSON 备份到这里</p>
        <button className="button button-secondary" disabled={busy} onClick={() => fileInput.current?.click()}>{busy ? '正在校验…' : '选择备份文件'}</button>
        <input ref={fileInput} className="visually-hidden" type="file" accept=".json,application/json" tabIndex={-1} onChange={event => {
          const file = event.target.files?.[0]
          if (file) void readFile(file)
          event.target.value = ''
        }} />
      </div>}
      {preview && <div className="import-preview">
        <div className="preview-heading"><div><h4>导入预览</h4><p>{sourceName}</p></div><span>校验通过</span></div>
        <PreviewTable title="时间线" stats={preview.timelineStats} />
        <PreviewTable title="嵌套事件" stats={preview.eventStats} />
        {conflictCount > 0 && <fieldset className="conflict-options">
          <legend>如何处理全部 {conflictCount} 项冲突？</legend>
          <label><input type="radio" name="conflict" checked={resolution === 'keepLocal'} onChange={() => setResolution('keepLocal')} /><span>保留本地<small>跳过备份中的冲突版本，新增内容仍会导入</small></span></label>
          <label><input type="radio" name="conflict" checked={resolution === 'useBackup'} onChange={() => setResolution('useBackup')} /><span>使用备份<small>仅替换冲突的时间线字段与事件内容，不删除其他记录</small></span></label>
        </fieldset>}
        <div className="form-actions">
          <button className="button button-secondary" onClick={cancelImport} disabled={busy}>取消</button>
          <button className="button button-primary" disabled={busy || (conflictCount > 0 && resolution === null)} onClick={() => void confirmImport()}>{busy ? '导入中…' : '确认导入'}</button>
        </div>
      </div>}
    </section>
    {error && <p className="form-error" role="alert">{error}</p>}
    {status && <p className="success-message" role="status">{status}</p>}
  </Modal>
}