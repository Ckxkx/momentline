import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, FormEvent } from 'react'
import type { Timeline } from '../types'
import type { TimelineStoreController } from '../hooks/useTimelineStore'
import { graphemeLength } from '../lib/backup'
import { ConcurrentModificationError } from '../lib/store'
import { Modal } from './Modal'

const tones = [
  ['#5B7C6E', '松柏'], ['#517A9A', '晴海'], ['#8A6D9B', '鸢尾'], ['#B06B5D', '陶土'],
  ['#B18445', '麦金'], ['#63758A', '雾蓝'], ['#7B844F', '橄榄'], ['#A55D78', '蔷薇']
] as const

interface TimelineEditorProps {
  timeline: Timeline | null
  store: TimelineStoreController
  onClose: () => void
}

export function TimelineEditor({ timeline, store, onClose }: TimelineEditorProps) {
  const [title, setTitle] = useState(timeline?.title ?? '')
  const [tone, setTone] = useState(timeline?.tone ?? tones[0][0])
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const initialValues = useRef({ title, tone })
  const dirty = title !== initialValues.current.title || tone !== initialValues.current.tone

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
    if (!dirty || window.confirm('放弃未保存的时间线更改？')) onClose()
  }

  const save = async (force: boolean) => {
    if (timeline) await store.updateTimeline(timeline, { title, tone }, force)
    else await store.addTimeline({ title, tone })
    onClose()
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await save(false)
    } catch (caught) {
      if (timeline && caught instanceof ConcurrentModificationError) {
        if (window.confirm('该时间线已在其他页面被修改。仍要应用当前名称和颜色吗？事件会保留最新版本。')) {
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

  return <Modal title={timeline ? '编辑时间线' : '新建时间线'} onClose={requestClose}>
    <form className="editor-form" onSubmit={submit} noValidate>
      <label className="field">
        <span className="field-label">时间线名称</span>
        <input data-autofocus value={title} onChange={event => setTitle(event.target.value)} placeholder="例如：我们的旅行" maxLength={160} required />
        <span className="field-meta">{graphemeLength(title)}/80</span>
      </label>
      <fieldset className="tone-field">
        <legend className="field-label">主题颜色</legend>
        <div className="tone-options">
          {tones.map(([value, label]) => <label key={value} className={`tone-option ${tone === value ? 'selected' : ''}`} style={{ '--tone': value } as CSSProperties}>
            <input type="radio" name="tone" value={value} checked={tone === value} onChange={() => setTone(value)} />
            <span className="tone-swatch" aria-hidden="true" />
            <span>{label}</span>
          </label>)}
        </div>
      </fieldset>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions">
        <button className="button button-secondary" type="button" onClick={requestClose}>取消</button>
        <button className="button button-primary" type="submit" disabled={saving}>{saving ? '保存中…' : '保存时间线'}</button>
      </div>
    </form>
  </Modal>
}