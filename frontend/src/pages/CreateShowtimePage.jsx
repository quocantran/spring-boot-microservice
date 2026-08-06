import { useState, useEffect } from 'react'
import { apiFetch } from '../utils/api'
import { formatVND, formatTimeShort } from '../utils/format'

export default function CreateShowtimePage({ movieId, addToast, onBack }) {
  const [movie, setMovie] = useState(null)
  const [loading, setLoading] = useState(false)
  const [form, setForm] = useState({
    hall: 'Phòng A',
    startTime: '',
    price: '100000',
  })

  useEffect(() => {
    apiFetch(`/movies/${movieId}`).then(({ ok, data }) => {
      if (ok && data) setMovie(data)
    })
  }, [movieId])

  const handleChange = (field) => (e) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.hall || !form.startTime || !form.price) {
      addToast('error', 'Vui lòng nhập đủ thông tin suất chiếu')
      return
    }
    setLoading(true)
    try {
      const { ok, data } = await apiFetch(`/movies/${movieId}/showtimes`, {
        method: 'POST',
        body: JSON.stringify({
          hall: form.hall,
          startTime: form.startTime,
          price: parseInt(form.price, 10),
        }),
      })
      if (ok) {
        addToast('success', `✅ Tạo suất chiếu thành công! (${data.seatsGenerated || 40} ghế đã tạo)`)
        setForm({ hall: 'Phòng A', startTime: '', price: '100000' })
      } else {
        addToast('error', data?.message || 'Tạo suất chiếu thất bại')
      }
    } catch (err) {
      addToast('error', `Lỗi: ${err.message}`)
    }
    setLoading(false)
  }

  const inputStyle = {
    width: '100%',
    padding: '10px 14px',
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    borderRadius: 8,
    color: 'var(--text)',
    fontSize: '0.9rem',
    fontFamily: 'inherit',
    outline: 'none',
    boxSizing: 'border-box',
    transition: 'border-color 0.2s',
  }

  const labelStyle = {
    fontSize: '0.85rem',
    color: 'var(--text-muted)',
    marginBottom: 6,
    display: 'block',
    fontWeight: 600,
  }

  return (
    <div>
      <button className="back-btn" onClick={onBack}>← Quay lại</button>

      <div style={{ maxWidth: 640, margin: '0 auto' }}>
        <h1 className="section-title">🕐 Thêm suất chiếu mới</h1>
        {movie && (
          <p className="section-subtitle">
            Tạo suất chiếu cho phim <strong>"{movie.title}"</strong> — Ghế sẽ được tự động tạo (5 hàng × 8 ghế)
          </p>
        )}

        <div className="card" style={{ padding: 32 }}>
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>Phòng chiếu *</label>
              <select value={form.hall} onChange={handleChange('hall')} style={inputStyle}>
                <option value="Phòng A">Phòng A</option>
                <option value="Phòng B">Phòng B</option>
                <option value="Phòng C">Phòng C</option>
                <option value="Phòng D">Phòng D</option>
                <option value="Phòng VIP">Phòng VIP</option>
              </select>
            </div>

            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>Thời gian chiếu *</label>
              <input
                type="datetime-local"
                value={form.startTime}
                onChange={handleChange('startTime')}
                style={inputStyle}
                required
              />
            </div>

            <div style={{ marginBottom: 24 }}>
              <label style={labelStyle}>Giá vé (VNĐ) *</label>
              <input
                type="number"
                value={form.price}
                onChange={handleChange('price')}
                placeholder="100000"
                min="10000"
                step="10000"
                style={inputStyle}
                required
              />
              <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4, display: 'block' }}>
                {form.price ? `${formatVND(parseInt(form.price, 10))} / vé` : ''}
              </small>
            </div>

            <div style={{
              marginBottom: 24,
              padding: 16,
              background: 'rgba(16, 185, 129, 0.06)',
              borderRadius: 8,
              border: '1px solid rgba(16, 185, 129, 0.15)',
            }}>
              <p style={{ fontSize: '0.85rem', color: 'var(--success)', fontWeight: 600, marginBottom: 4 }}>
                🪑 Tự động tạo 40 ghế (5 hàng × 8 cột)
              </p>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Hàng A–E, mỗi hàng 8 ghế. Ghế sẽ được tạo ngay sau khi tạo suất chiếu.
              </p>
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} disabled={loading}>
              {loading ? '⏳ Đang tạo...' : '🕐 Tạo suất chiếu'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
