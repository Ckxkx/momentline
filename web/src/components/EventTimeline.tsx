import { useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import type { EventOrder, Timeline, TimelineEvent } from '../types'
import { sortEvents } from '../lib/backup'
import { formatDate, formatFullDate, formatTime } from '../lib/format'
import { Icon } from './Icons'

interface EventTimelineProps {
  timeline: Timeline
  order: EventOrder
  onOrderChange: (order: EventOrder) => void
  onBack: () => void
  onAdd: () => void
  onEditTimeline: () => void
  onOpen: (event: TimelineEvent) => void
  onEdit: (event: TimelineEvent) => void
  onDelete: (event: TimelineEvent) => void
}

interface EventGroup {
  key: string
  day: Date
  events: TimelineEvent[]
}

export function EventTimeline({ timeline, order, onOrderChange, onBack, onAdd, onEditTimeline, onOpen, onEdit, onDelete }: EventTimelineProps) {
  const [query, setQuery] = useState('')
  const keyword = query.trim().toLocaleLowerCase()
  const filtered = useMemo(() => {
    const sorted = sortEvents(timeline.events, order)
    if (!keyword) return sorted
    return sorted.filter(event => event.title.toLocaleLowerCase().includes(keyword) || event.body.toLocaleLowerCase().includes(keyword))
  }, [keyword, order, timeline.events])
  const groups = useMemo(() => {
    const result: EventGroup[] = []
    for (const event of filtered) {
      const date = new Date(event.occurredAt)
      const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
      const existing = result.at(-1)
      if (existing?.key === key) existing.events.push(event)
      else result.push({ key, day: date, events: [event] })
    }
    return result
  }, [filtered])

  return <div className="event-view" style={{ '--tone': timeline.tone } as CSSProperties}>
    <button className="back-button" onClick={onBack}><Icon name="back" />全部时间线</button>
    <section className="timeline-detail-header">
      <div className="detail-title-row">
        <span className="detail-tone" aria-hidden="true" />
        <div>
          <p className="eyebrow">TIMELINE</p>
          <h1>{timeline.title}</h1>
        </div>
      </div>
      <div className="detail-meta">
        <span>{timeline.events.length} 条事件</span>
        <span>创建于 {formatDate(timeline.createdAt)}</span>
        <button className="text-button" onClick={onEditTimeline}><Icon name="edit" />编辑时间线</button>
      </div>
    </section>

    {timeline.events.length === 0 ? <section className="empty-state compact">
      <span className="empty-symbol"><Icon name="note" /></span>
      <h2>这条时间线还没有事件</h2>
      <p>记录第一件值得留下的事，发生时间可以是过去、现在或未来。</p>
      <button className="button button-primary" onClick={onAdd}><Icon name="plus" />记录首个事件</button>
    </section> : <>
      <div className="list-toolbar">
        <div className="search-wrap">
          <Icon name="search" />
          <input value={query} onChange={event => setQuery(event.target.value)} placeholder="搜索事件标题或备注" aria-label="搜索当前时间线事件" type="search" />
          {query && <button className="search-clear" onClick={() => setQuery('')} aria-label="清除事件搜索">清除</button>}
        </div>
        <label className="sort-control">
          <span className="visually-hidden">事件排序</span>
          <select value={order} onChange={event => onOrderChange(event.target.value as EventOrder)}>
            <option value="newestFirst">发生时间 · 倒序</option>
            <option value="oldestFirst">发生时间 · 正序</option>
          </select>
        </label>
      </div>
      {keyword && <p className="result-count" role="status">找到 {filtered.length} 条事件</p>}
      {filtered.length === 0 ? <section className="empty-state compact">
        <h2>没有找到相关事件</h2>
        <p>搜索仅作用于当前时间线，可清除关键词查看全部事件。</p>
        <button className="button button-secondary" onClick={() => setQuery('')}>清除搜索</button>
      </section> : <div className="event-timeline">
        {groups.map(group => <section className="day-group" key={group.key}>
          <h2 className="day-heading"><time>{formatFullDate(group.day)}</time><span>{group.events.length}</span></h2>
          <div className="event-list">
            {group.events.map(event => <article className="event-card" key={event.id}>
              <div className="timeline-marker" aria-hidden="true"><span /></div>
              <button className="event-open" onClick={() => onOpen(event)} aria-label={`查看事件 ${event.title}`}>
                <time dateTime={event.occurredAt}>{formatTime(event.occurredAt)}</time>
                <strong>{event.title}</strong>
                {event.body && <p>{event.body}</p>}
              </button>
              <div className="event-actions">
                <button className="icon-button" onClick={() => onEdit(event)} aria-label={`编辑事件 ${event.title}`}><Icon name="edit" /></button>
                <button className="icon-button danger-hover" onClick={() => onDelete(event)} aria-label={`删除事件 ${event.title}`}><Icon name="trash" /></button>
              </div>
            </article>)}
          </div>
        </section>)}
      </div>}
    </>}
  </div>
}