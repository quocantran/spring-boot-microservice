import { useState, useEffect, useCallback, useRef } from 'react'
import { apiFetch } from '../utils/api'
import { formatVND, formatTime } from '../utils/format'
import { STATUS_MAP } from '../constants/status'

export default function BookingDetailPage({ bookingId, onBack }) {
  const [booking, setBooking] = useState(null)
  const [movie, setMovie] = useState(null)
  const [loading, setLoading] = useState(true)
  const intervalRef = useRef(null)

  const fetchBooking = useCallback(() => {
    apiFetch(`/bookings/${bookingId}`).then(({ data }) => {
      setBooking(data)
      setLoading(false)
      if (data && (data.status === 'CONFIRMED' || data.status === 'CANCELLED')) {
        if (intervalRef.current) clearInterval(intervalRef.current)
      }
      if (data?.movieId && !movie) {
        apiFetch(`/movies/${data.movieId}`).then(({ ok, data: m }) => {
          if (ok && m) setMovie(m)
        })
      }
    })
  }, [bookingId])

  useEffect(() => {
    fetchBooking()
    intervalRef.current = setInterval(fetchBooking, 2000)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [fetchBooking])

  if (loading) return <div className="loading"><div className="spinner" />Đang tải thông tin vé...</div>
  if (!booking) return <div className="loading">Không tìm thấy đơn đặt vé</div>

  const statusInfo = STATUS_MAP[booking.status] || { label: booking.status, icon: '❓', color: 'var(--text-muted)' }
  const isFinal = booking.status === 'CONFIRMED' || booking.status === 'CANCELLED'
  const isCancelled = booking.status === 'CANCELLED'

  const inferredFailedStep = (() => {
    if (booking.failedStep) return booking.failedStep
    const reason = (booking.failureReason || '').toLowerCase()
    if (reason.includes('số dư') || reason.includes('thanh toán')) {
      return 'PAYMENT_PROCESSED'
    }
    return 'SEATS_RESERVED'
  })()

  const steps = [
    { key: 'PENDING', label: 'Tạo đơn', icon: '📝', done: true },
    { key: 'SEATS_RESERVED', label: 'Giữ ghế', icon: '🪑', done: ['SEATS_RESERVED', 'PAYMENT_PROCESSED', 'CONFIRMED'].includes(booking.status) },
    { key: 'PAYMENT_PROCESSED', label: 'Thanh toán', icon: '💳', done: ['PAYMENT_PROCESSED', 'CONFIRMED'].includes(booking.status) },
    { key: 'CONFIRMED', label: 'Xác nhận', icon: '✅', done: booking.status === 'CONFIRMED' },
  ]

  const progressPercent = (() => {
    if (!isCancelled) {
      return `${(steps.filter(s => s.done).length - 1) / (steps.length - 1) * 100}%`
    }
    if (inferredFailedStep === 'PAYMENT_PROCESSED') return '66%'
    return '33%'
  })()

  const movieTitle = movie?.title || booking.movieId

  return (
    <div style={{ maxWidth: 640, margin: '0 auto' }}>
      <button className="back-btn" onClick={onBack}>← Quay lại đơn đặt vé</button>

      <div className="card" style={{ padding: 32, textAlign: 'center', marginBottom: 24 }}>
        <div style={{ fontSize: '3rem', marginBottom: 8 }}>{statusInfo.icon}</div>
        <h2 style={{ marginBottom: 8, color: statusInfo.color }}>{statusInfo.label}</h2>
        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Mã đơn: #{booking.id.substring(0, 8)}</p>

        {!isFinal && (
          <div style={{ marginTop: 12 }}>
            <span className="polling-badge"><span className="polling-dot" /> Đang theo dõi trạng thái...</span>
          </div>
        )}
      </div>

      <div className="card" style={{ padding: 24, marginBottom: 24 }}>
        <h3 style={{ marginBottom: 20, fontSize: '0.95rem' }}>📊 Tiến trình Saga</h3>
        <div style={{ display: 'flex', justifyContent: 'space-between', position: 'relative' }}>
          <div style={{ position: 'absolute', top: 18, left: 36, right: 36, height: 3, background: 'var(--border)', borderRadius: 2, zIndex: 0 }}>
            <div style={{
              width: progressPercent,
              height: '100%',
              background: isCancelled ? 'var(--danger)' : 'var(--success)',
              borderRadius: 2,
              transition: 'width 0.5s ease',
            }} />
          </div>

          {steps.map((step, i) => (
            <div key={step.key} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, zIndex: 1, flex: 1 }}>
              {(() => {
                const isFailedStep = isCancelled && step.key === inferredFailedStep
                const isCompletedBeforeFail = isCancelled && (
                  (inferredFailedStep === 'SEATS_RESERVED' && step.key === 'PENDING') ||
                  (inferredFailedStep === 'PAYMENT_PROCESSED' && ['PENDING', 'SEATS_RESERVED'].includes(step.key))
                )
                const isDone = isCompletedBeforeFail || (!isCancelled && step.done)
                return (
                  <>
                    <div style={{
                      width: 36, height: 36, borderRadius: '50%',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '1rem',
                      background: isFailedStep ? 'var(--danger)'
                        : isDone ? 'var(--success)'
                        : 'var(--bg-hover)',
                      border: `2px solid ${isFailedStep ? 'var(--danger)' : isDone ? 'var(--success)' : 'var(--border)'}`,
                      transition: 'all 0.3s',
                    }}>
                      {isFailedStep ? '✕' : isDone ? '✓' : String(i + 1)}
                    </div>
                    <span style={{
                      fontSize: '0.7rem',
                      color: isFailedStep ? 'var(--danger)'
                        : isDone ? 'var(--success)'
                        : 'var(--text-muted)',
                    }}>{step.label}</span>
                  </>
                )
              })()}
            </div>
          ))}
        </div>

        {isCancelled && (
          <div style={{ marginTop: 20, padding: 12, background: 'rgba(239, 68, 68, 0.1)', borderRadius: 8, border: '1px solid rgba(239, 68, 68, 0.2)' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--danger)', marginBottom: 6 }}>
              ❌ Đơn đã bị hủy ở bước {inferredFailedStep === 'PAYMENT_PROCESSED' ? 'thanh toán' : 'giữ ghế'}
            </p>
            <p style={{ fontSize: '0.85rem', color: 'var(--danger)' }}>
              Lý do: {booking.failureReason || 'Không xác định'}
            </p>
          </div>
        )}
      </div>

      <div className="card" style={{ padding: 24 }}>
        <h3 style={{ marginBottom: 16, fontSize: '0.95rem' }}>📋 Chi tiết đơn</h3>
        <div className="summary-row">
          <span style={{ color: 'var(--text-muted)' }}>Mã đơn</span>
          <span style={{ fontSize: '0.8rem', fontFamily: 'monospace' }}>{booking.id}</span>
        </div>
        <div className="summary-row">
          <span style={{ color: 'var(--text-muted)' }}>Phim</span>
          <span style={{ fontWeight: 600 }}>{movieTitle}</span>
        </div>
        {movie && (
          <div className="summary-row">
            <span style={{ color: 'var(--text-muted)' }}>Thể loại</span>
            <span>{movie.genre}</span>
          </div>
        )}
        <div className="summary-row">
          <span style={{ color: 'var(--text-muted)' }}>Ghế</span>
          <span>{Array.isArray(booking.seatIds) ? booking.seatIds.map(s => s.split('-').pop()).join(', ') : booking.seatIds}</span>
        </div>
        <div className="summary-row" style={{ borderBottom: 'none' }}>
          <span style={{ fontWeight: 700 }}>Tổng cộng</span>
          <span className="summary-total">{formatVND(booking.totalAmount)}</span>
        </div>
        <div style={{ marginTop: 8, fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          Tạo lúc: {formatTime(booking.createdAt)}
        </div>
      </div>
    </div>
  )
}
