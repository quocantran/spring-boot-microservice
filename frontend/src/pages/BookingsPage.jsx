import { useState, useEffect, useCallback } from 'react'
import { apiFetch } from '../utils/api'
import { formatVND, formatTime } from '../utils/format'
import { STATUS_MAP } from '../constants/status'

export default function BookingsPage({ onViewDetail }) {
  const [bookings, setBookings] = useState([])
  const [movies, setMovies] = useState({})
  const [loading, setLoading] = useState(true)

  const fetchData = useCallback(async () => {
    const [bookingsRes, moviesRes] = await Promise.all([
      apiFetch('/bookings'),
      apiFetch('/movies'),
    ])
    setBookings(bookingsRes.data || [])
    if (moviesRes.ok && moviesRes.data) {
      const map = {}
      moviesRes.data.forEach(m => { map[m.id] = m })
      setMovies(map)
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    fetchData()
    const interval = setInterval(fetchData, 5000)
    return () => clearInterval(interval)
  }, [fetchData])

  if (loading) return <div className="loading"><div className="spinner" />Đang tải đơn đặt vé...</div>

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
        <h1 className="section-title" style={{ marginBottom: 0 }}>📋 Đơn đặt vé</h1>
        <span className="polling-badge"><span className="polling-dot" /> Tự động cập nhật</span>
      </div>

      {bookings.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: 48 }}>
          <p style={{ fontSize: '2rem', marginBottom: 8 }}>📭</p>
          <p style={{ color: 'var(--text-muted)' }}>Chưa có đơn đặt vé nào</p>
        </div>
      ) : (
        <div className="card" style={{ padding: 0 }}>
          {bookings.map(booking => {
            const statusInfo = STATUS_MAP[booking.status] || { label: booking.status, icon: '❓', color: 'var(--text-muted)' }
            const movieTitle = movies[booking.movieId]?.title || booking.movieId.substring(0, 8)
            return (
              <div key={booking.id} className="booking-item" style={{ cursor: 'pointer' }} onClick={() => onViewDetail(booking.id)}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                  <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>#{booking.id.substring(0, 8)}</span>
                  <span className={`status-badge status-${booking.status}`}>
                    {statusInfo.icon} {statusInfo.label}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 16, fontSize: '0.85rem', color: 'var(--text-muted)', flexWrap: 'wrap' }}>
                  <span>🎬 {movieTitle}</span>
                  <span>🪑 {Array.isArray(booking.seatIds) ? booking.seatIds.map(s => s.split('-').pop()).join(', ') : booking.seatIds}</span>
                  <span>💰 {formatVND(booking.totalAmount)}</span>
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  {formatTime(booking.createdAt)} <span style={{ color: 'var(--primary-light)', marginLeft: 8 }}>Xem chi tiết →</span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
