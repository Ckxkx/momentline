import { useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import type { Timeline, TimelineOrder } from '../types'
import { sortTimelines } from '../lib/backup'
import { formatDate } from '../lib/format'
import { Icon } from './Icons'

interface TimelineListProps {
  timelines: Timeline[]
  order: TimelineOrder
  onOrderChange: (order: TimelineOrder) => void
  onAdd: () => void
  onOpen: (timeline: Timeline) => void
  onEdit: (timeline: Timeline) => void
  onDelete: (timeline: Timeline) => void
  onOpenData: () => void
}

export function TimelineList({ timelines, order, onOrderChange, onAdd, onOpen, onEdit, onDelete, onOpenData }: TimelineListProps) {
  const [query, setQuery] = useState('')
  const keyword = query.trim().toLocaleLowerCase()
  const filtered = useMemo(() => {
    const sorted = sortTimelines(timelines, order)
    if (!keyword) return sorted
    return sorted.filter(timeline => timeline.title.toLocaleLowerCase().includes(keyword))
  }, [keyword, order, timelines])

  if (timelines.length === 0) {
    return <section className="empty-state">
      <span className="empty-mark" aria-hidden="true"><i /><i /><i /></span>
      <p className="eyebrow">YOUR STORIES, IN ORDER</p>
      <h2>从第一条时间线开始</h2>
      <p>按旅行、成长、工作或任何主题整理经历。<br />内容仅保存在当前浏览器，也可以随时导入完整备份。</p>
      <div className="empty-actions">
        <button className="button button-primary" onClick={onAdd}><Icon name="plus" />新建时间线</button>
        <button className="button button-secondary" onClick={onOpenData}><Icon name="upload" />导入备份</button>
      </div>
    </section>
  }

  return <>
    <div className="list-toolbar">
      <div className="search-wrap">
        <Icon name="search" />
        <input value={query} onChange={event => setQuery(event.target.value)} placeholder="搜索时间线名称" aria-label="搜索时间线" type="search" />
        {query && <button className="search-clear" onClick={() => setQuery('')} aria-label="清除时间线搜索">清除</button>}
      </div>
      <label className="sort-control">
        <span className="visually-hidden">时间线排序</span>
        <select value={order} onChange={event => onOrderChange(event.target.value as TimelineOrder)}>
          <option value="createdDesc">创建时间 · 倒序</option>
          <option value="createdAsc">创建时间 · 正序</option>
          <option value="titleAsc">名称 · 正序</option>
          <option value="titleDesc">名称 · 倒序</option>
        </select>
      </label>
    </div>
    {keyword && <p className="result-count" role="status">找到 {filtered.length} 条时间线</p>}
    {filtered.length === 0 ? <section className="empty-state compact">
      <h2>没有找到相关时间线</h2>
      <p>换个关键词，或清除搜索后查看全部时间线。</p>
      <button className="button button-secondary" onClick={() => setQuery('')}>清除搜索</button>
    </section> : <div className="timeline-grid">
      {filtered.map(timeline => <article className="timeline-card" key={timeline.id} style={{ '--tone': timeline.tone } as CSSProperties}>
        <span className="tone-rule" aria-hidden="true" />
        <button className="timeline-open" onClick={() => onOpen(timeline)} aria-label={`打开时间线 ${timeline.title}，${timeline.events.length} 条事件`}>
          <span className="timeline-card-top">
            <span className="tone-dot" aria-hidden="true" />
            <span className="event-count">{timeline.events.length} 条事件</span>
          </span>
          <strong>{timeline.title}</strong>
          <span className="timeline-created">创建于 {formatDate(timeline.createdAt)}</span>
          <span className="open-hint">查看时间线 <Icon name="chevron" /></span>
        </button>
        <div className="card-actions">
          <button className="icon-button" onClick={() => onEdit(timeline)} aria-label={`编辑时间线 ${timeline.title}`}><Icon name="edit" /></button>
          <button className="icon-button danger-hover" onClick={() => onDelete(timeline)} aria-label={`删除时间线 ${timeline.title}`}><Icon name="trash" /></button>
        </div>
      </article>)}
    </div>}
  </>
}