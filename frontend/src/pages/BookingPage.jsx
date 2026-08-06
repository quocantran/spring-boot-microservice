import { useState, useEffect } from 'react'
import { apiFetch, getToken } from '../utils/api'
import { formatVND, formatTimeShort } from '../utils/format'
import { useNavigate } from 'react-router-dom'

export default function BookingPage({ movieId, initialMovie = null, onBack, addToast, onBookingCreated }) {
  const [movie, setMovie] = useState(initialMovie)
  const [movieLoading, setMovieLoading] = useState(!initialMovie)
  const [showtimes, setShowtimes] = useState([])
  const [selectedShowtime, setSelectedShowtime] = useState(null)
  const [selectedSeats, setSelectedSeats] = useState([])
  const [seats, setSeats] = useState([])
  const [seatsLoading, setSeatsLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const navigate = useNavigate()

  const isAdmin = (() => {
    try {
      const token = getToken()
      if (!token) return false
      const payload = JSON.parse(atob(token.split('.')[1]))
      return payload.role === 'ADMIN'
    } catch { return false }
  })()

  useEffect(() => {
    let active = true
    setMovieLoading(true)
    apiFetch(`/movies/${movieId}`)
      .then(({ ok, data }) => {
        if (!active) return
        if (ok && data) setMovie(data)
      })
      .finally(() => { if (active) setMovieLoading(false) })
    return () => { active = false }
  }, [movieId])

  useEffect(() => {
    apiFetch(`/movies/${movieId}/showtimes`).then(({ data }) => {
      setShowtimes(data || [])
      if (data?.length) setSelectedShowtime(data[0])
    }).finally(() => setLoading(false))
  }, [movieId])

  useEffect(() => {
    if (!selectedShowtime) return
    setSeatsLoading(true)
    setSelectedSeats([])
    apiFetch(`/seats?showtimeId=${selectedShowtime.id}`)
      .then(({ data }) => setSeats(data || []))
      .finally(() => setSeatsLoading(false))
  }, [selectedShowtime?.id])

  const toggleSeat = (seatId, seatStatus) => {
    if (seatStatus !== 'AVAILABLE') return
    setSelectedSeats(prev =>
      prev.includes(seatId) ? prev.filter(s => s !== seatId) : [...prev, seatId]
    )
  }

  const totalAmount = selectedShowtime ? selectedSeats.length * Number(selectedShowtime.price) : 0

  const handleBooking = async () => {
    if (!selectedShowtime || selectedSeats.length === 0 || !movie) return
    setSubmitting(true)
    try {
      const { ok, data } = await apiFetch('/bookings', {
        method: 'POST',
        body: JSON.stringify({
          movieId: movie.id || movieId,
          showtimeId: selectedShowtime.id,
          seatIds: selectedSeats,
          totalAmount,
        }),
      })
      if (ok) {
        addToast('success', `✅ Đơn đặt vé #${data.id.substring(0, 8)} đã tạo!`)
        onBookingCreated(data.id)
      } else {
        addToast('error', `❌ Lỗi: ${data?.message || 'Không xác định'}`)
      }
    } catch (err) {
      addToast('error', `❌ Không thể kết nối: ${err.message}`)
    }
    setSubmitting(false)
  }

  if (movieLoading) return <div className="loading"><div className="spinner" />Đang tải thông tin phim...</div>
  if (!movie) return <div className="loading">Không tìm thấy phim</div>
  if (loading) return <div className="loading"><div className="spinner" />Đang tải suất chiếu...</div>

  const seatsByRow = {}
  seats.forEach(seat => {
    if (!seatsByRow[seat.seatRow]) seatsByRow[seat.seatRow] = []
    seatsByRow[seat.seatRow].push(seat)
  })
  const sortedRows = Object.keys(seatsByRow).sort()
  sortedRows.forEach(row => {
    seatsByRow[row].sort((a, b) => a.seatNumber.localeCompare(b.seatNumber, undefined, { numeric: true }))
  })

  return (
    <div>
      <button className="back-btn" onClick={onBack}>← Quay lại danh sách phim</button>
      <h1 className="section-title">{movie.title}</h1>

      <div className="booking-movie-hero card">
        {movie.posterUrl ? (
          <img
            src={movie.posterUrl}
            alt={movie.title}
            className="booking-movie-poster"
            onError={(e) => {
              e.target.style.display = 'none'
              const fallback = e.target.nextSibling
              if (fallback) fallback.style.display = 'flex'
            }}
          />
        ) : null}
        <div
          className="booking-movie-poster-placeholder"
          style={movie.posterUrl ? { display: 'none' } : {}}
        >
          🎬
        </div>

        <div className="booking-movie-content">
          <p className="booking-movie-label">Thông tin phim</p>
          <h2 className="booking-movie-title">{movie.title}</h2>
          <div className="booking-movie-meta">
            {movie.genre ? <span className="tag tag-genre">{movie.genre}</span> : null}
            {movie.duration ? <span className="tag tag-duration">{movie.duration} phút</span> : null}
          </div>
          <p className="booking-movie-description">
            {movie.description && movie.description.trim().length > 0
              ? movie.description
              : 'Phim hiện chưa có mô tả chi tiết.'}
          </p>
        </div>
      </div>

      <div className="booking-layout">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
            <h3 style={{ margin: 0 }}>🕐 Chọn suất chiếu</h3>
            {isAdmin && (
              <button
                className="btn"
                style={{ fontSize: '0.75rem', padding: '4px 10px' }}
                onClick={() => navigate(`/movies/${movieId}/showtimes/new`)}
              >
                ➕ Thêm suất chiếu
              </button>
            )}
          </div>
          <div className="showtime-grid">
            {showtimes.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                Chưa có suất chiếu nào.
                {isAdmin && ' Hãy thêm suất chiếu mới.'}
              </p>
            ) : showtimes.map(st => (
              <button key={st.id}
                className={`showtime-btn ${selectedShowtime?.id === st.id ? 'selected' : ''}`}
                onClick={() => { setSelectedShowtime(st); setSelectedSeats([]) }}>
                <div>{st.hall}</div>
                <div>{formatTimeShort(st.startTime)}</div>
                <div className="showtime-price">{formatVND(st.price)}</div>
              </button>
            ))}
          </div>

          {selectedShowtime && (
            <>
              <h3 style={{ marginTop: 32, marginBottom: 12 }}>🪑 Chọn ghế ngồi</h3>
              {seatsLoading ? (
                <div className="loading"><div className="spinner" />Đang tải sơ đồ ghế...</div>
              ) : (
                <div className="seat-map">
                  <div className="screen" />
                  {sortedRows.map(row => (
                    <div key={row} className="seat-row">
                      <span className="seat-row-label">{row}</span>
                      {seatsByRow[row].map(seat => {
                        const isSelected = selectedSeats.includes(seat.id)
                        const isBooked = seat.status === 'BOOKED' || seat.status === 'HELD'
                        return (
                          <div key={seat.id}
                            className={`seat ${isSelected ? 'seat-selected' : ''} ${isBooked ? 'seat-booked' : ''}`}
                            onClick={() => toggleSeat(seat.id, seat.status)}
                            title={isBooked ? `Ghế ${seat.seatNumber} — Đã đặt` : `Ghế ${seat.seatNumber}`}>
                            {seat.seatNumber}
                          </div>
                        )
                      })}
                      <span className="seat-row-label">{row}</span>
                    </div>
                  ))}
                </div>
              )}
              <div className="seat-legend">
                <div className="seat-legend-item">
                  <div className="seat-legend-box" style={{ background: 'var(--bg-hover)', border: '2px solid var(--border)' }} />Trống
                </div>
                <div className="seat-legend-item">
                  <div className="seat-legend-box" style={{ background: 'var(--primary)' }} />Đã chọn
                </div>
                <div className="seat-legend-item">
                  <div className="seat-legend-box" style={{ background: 'var(--danger)', opacity: 0.6 }} />Đã đặt
                </div>
              </div>
            </>
          )}
        </div>

        <div className="summary-panel">
          <h3 style={{ marginBottom: 16 }}>🎫 Thông tin đặt vé</h3>
          <div className="summary-row">
            <span style={{ color: 'var(--text-muted)' }}>Phim</span>
            <span style={{ fontWeight: 600, maxWidth: 180, textAlign: 'right' }}>{movie.title}</span>
          </div>
          <div className="summary-row">
            <span style={{ color: 'var(--text-muted)' }}>Suất chiếu</span>
            <span>{selectedShowtime ? `${selectedShowtime.hall} — ${formatTimeShort(selectedShowtime.startTime)}` : '—'}</span>
          </div>
          <div className="summary-row">
            <span style={{ color: 'var(--text-muted)' }}>Ghế</span>
            <span>{selectedSeats.length > 0 ? selectedSeats.map(s => {
              const seat = seats.find(st => st.id === s)
              return seat ? seat.seatNumber : s.split('-').pop()
            }).join(', ') : '—'}</span>
          </div>
          <div className="summary-row">
            <span style={{ color: 'var(--text-muted)' }}>Số lượng</span>
            <span>{selectedSeats.length} vé</span>
          </div>
          <div className="summary-row" style={{ borderBottom: 'none', paddingTop: 12 }}>
            <span style={{ fontWeight: 700 }}>Tổng cộng</span>
            <span className="summary-total">{formatVND(totalAmount)}</span>
          </div>

          <button className="btn btn-primary"
            style={{ width: '100%', marginTop: 20, justifyContent: 'center' }}
            disabled={selectedSeats.length === 0 || submitting}
            onClick={handleBooking}>
            {submitting ? '⏳ Đang tạo đơn...' : '🎫 Đặt vé ngay'}
          </button>
        </div>
      </div>
    </div>
  )
}
