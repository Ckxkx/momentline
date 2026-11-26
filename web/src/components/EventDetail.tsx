import type { CSSProperties } from 'react'
import type { Timeline, TimelineEvent } from '../types'
import { formatDateTime } from '../lib/format'
import { Icon } from './Icons'
import { Modal } from './Modal'

interface EventDetailProps {
  event: TimelineEvent
  timeline: Timeline
  onClose: () => void
  onEdit: () => void
}

export function EventDetail({ event, timeline, onClose, onEdit }: EventDetailProps) {
  return <Modal title="事件详情" onClose={onClose}>
    <article className="event-detail" style={{ '--tone': timeline.tone } as CSSProperties}>
      <p className="form-context"><span />{timeline.title}</p>
      <h3>{event.title}</h3>
      <time className="detail-occurred" dateTime={event.occurredAt}>{formatDateTime(event.occurredAt)}</time>
      {event.body ? <p className="detail-body">{event.body}</p> : <p className="detail-body muted">未填写备注</p>}
      <dl className="event-metadata">
        <div><dt>创建时间</dt><dd>{formatDateTime(event.createdAt)}</dd></div>
        <div><dt>更新时间</dt><dd>{formatDateTime(event.updatedAt)}</dd></div>
      </dl>
      <div className="form-actions">
        <button className="button button-secondary" onClick={onClose}>关闭</button>
        <button className="button button-primary" onClick={onEdit}><Icon name="edit" />编辑事件</button>
      </div>
    </article>
  </Modal>
}