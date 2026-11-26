import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { Timeline, TimelineEvent } from '../types'
import type { TimelineStoreController } from '../hooks/useTimelineStore'
import { graphemeLength } from '../lib/backup'
import { fromLocalInputValue, toLocalInputValue } from '../lib/format'
import { ConcurrentModificationError } from '../lib/store'
import { Modal } from './Modal'

interface EventEditorProps {
  event: TimelineEvent | null
  timeline: Timeline
  store: TimelineStoreController
  onClose: () => void
}

export function EventEditor({ event, timeline, store, onClose }: EventEditorProps) {
  const [title, setTitle] = useState(event?.title ?? '')
  const [body, setBody] = useState(event?.body ?? '')
  const [occurredAt, setOccurredAt] = useState(() => toLocalInputValue(event?.occurredAt))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const initialValues = useRef({ title, body, occurredAt })
  const dirty = title !== initialValues.current.title || body !== initialValues.current.body || occurredAt !== initialValues.current.occurredAt

  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', beforeUnload)
    return () => window.removeEventListener('beforeunload', beforeUnload)
  }, [dirty])

  const requestClose = () => {
    if (!dirty || window.confirm('放弃未保存的事件更改？')) onClose()
  }

  const save = async (force: boolean) => {
    const draft = { title, body, occurredAt: fromLocalInputValue(occurredAt) }
    if (event) await store.updateEvent(event, timeline, draft, force)
    else await store.addEvent(timeline.id, draft)
    onClose()
  }

  const submit = async (formEvent: FormEvent) => {
    formEvent.preventDefault()
    setSaving(true)
    setError('')
    try {
      await save(false)
    } catch (caught) {
      if (event && caught instanceof ConcurrentModificationError) {
        if (window.confirm('该事件或所属时间线已在其他页面被修改。仍要应用当前事件内容吗？')) {
          try {
            await save(true)
            return
          } catch (retryError) {
            setError(retryError instanceof Error ? retryError.message : '暂时无法保存')
          }
        }
      } else {
        setError(caught instanceof Error ? caught.message : '暂时无法保存')
      }
    } finally {
      setSaving(false)
    }
  }

  return <Modal title={event ? '编辑事件' : '新事件'} onClose={requestClose}>
    <p className="form-context"><span style={{ background: timeline.tone }} />{timeline.title}</p>
    <form className="editor-form" onSubmit={submit} noValidate>
      <label className="field">
        <span className="field-label">标题</span>
        <input data-autofocus value={title} onChange={inputEvent => setTitle(inputEvent.target.value)} placeholder="给这件事一个标题" maxLength={200} required />
        <span className="field-meta">{graphemeLength(title)}/100</span>
      </label>
      <label className="field">
        <span className="field-label">发生时间</span>
        <input type="datetime-local" value={occurredAt} onChange={inputEvent => setOccurredAt(inputEvent.target.value)} required />
      </label>
      <label className="field">
        <span className="field-label">备注 <small>可选</small></span>
        <textarea value={body} onChange={inputEvent => setBody(inputEvent.target.value)} placeholder="写下值得记住的细节……" rows={8} maxLength={6000} />
        <span className="field-meta">{graphemeLength(body)}/5000，支持换行</span>
      </label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button className="button button-secondary" type="button" onClick={requestClose}>取消</button>
        <button className="button button-primary" type="submit" disabled={saving}>{saving ? '保存中…' : '保存事件'}</button>
      </div>
    </form>
  </Modal>
}