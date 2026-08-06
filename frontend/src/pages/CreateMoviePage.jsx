import { useState } from 'react'
import { apiFetch } from '../utils/api'

export default function CreateMoviePage({ addToast, onBack }) {
  const [loading, setLoading] = useState(false)
  const [form, setForm] = useState({
    title: '',
    genre: '',
    duration: '',
    posterUrl: '',
    description: '',
  })

  const handleChange = (field) => (e) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.title || !form.genre || !form.duration) {
      addToast('error', 'Vui lòng nhập đủ tiêu đề, thể loại và thời lượng')
      return
    }
    setLoading(true)
    try {
      const body = {
        title: form.title,
        genre: form.genre,
        duration: parseInt(form.duration, 10),
        posterUrl: form.posterUrl || undefined,
        description: form.description || undefined,
      }
      const { ok, data } = await apiFetch('/movies', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      if (ok) {
        addToast('success', `✅ Đã tạo phim "${form.title}" thành công!`)
        setForm({ title: '', genre: '', duration: '', posterUrl: '', description: '' })
      } else {
        addToast('error', data?.message || 'Tạo phim thất bại')
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
      <button className="back-btn" onClick={onBack}>← Quay lại danh sách phim</button>

      <div style={{ maxWidth: 640, margin: '0 auto' }}>
        <h1 className="section-title">🎬 Thêm phim mới</h1>
        <p className="section-subtitle">Chỉ ADMIN mới có thể tạo phim. Phim mới sẽ tự động sinh embedding AI.</p>

        <div className="card" style={{ padding: 32 }}>
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>Tiêu đề phim *</label>
              <input type="text" value={form.title} onChange={handleChange('title')}
                placeholder="Ví dụ: Avengers: Endgame" style={inputStyle} required />
            </div>

            <div style={{ marginBottom: 16 }}>
              <label style={labelStyle}>Thể loại *</label>
              <input type="text" value={form.genre} onChange={handleChange('genre')}
                placeholder="Ví dụ: Hành động, Khoa học viễn tưởng" style={inputStyle} required />
              <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4, display: 'block' }}>
                Nhiều thể loại cách nhau bởi dấu phẩy
              </small>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
              <div>
                <label style={labelStyle}>Thời lượng (phút) *</label>
                <input type="number" value={form.duration} onChange={handleChange('duration')}
                  placeholder="120" min="1" style={inputStyle} required />
              </div>
              <div>
                <label style={labelStyle}>URL Poster</label>
                <input type="url" value={form.posterUrl} onChange={handleChange('posterUrl')}
                  placeholder="https://image.tmdb.org/..." style={inputStyle} />
              </div>
            </div>

            {form.posterUrl && (
              <div style={{ marginBottom: 16, textAlign: 'center' }}>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: 8 }}>Xem trước poster:</p>
                <img src={form.posterUrl} alt="Preview" style={{
                  maxWidth: 200, maxHeight: 280, borderRadius: 8, border: '1px solid var(--border)',
                  objectFit: 'cover',
                }} onError={(e) => { e.target.style.display = 'none' }} />
              </div>
            )}

            <div style={{ marginBottom: 24 }}>
              <label style={labelStyle}>Mô tả phim</label>
              <textarea value={form.description} onChange={handleChange('description')}
                placeholder="Mô tả nội dung phim chi tiết (quan trọng cho AI gợi ý)..."
                rows={5} style={{ ...inputStyle, resize: 'vertical' }} />
              <small style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4, display: 'block' }}>
                ⚡ Mô tả chi tiết giúp AI gợi ý chính xác hơn (semantic similarity)
              </small>
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} disabled={loading}>
              {loading ? '⏳ Đang tạo...' : '🎬 Tạo phim mới'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
