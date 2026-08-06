import { useState, useEffect } from 'react'
import { apiFetch } from '../utils/api'

export default function MovieListPage({ onSelectMovie }) {
  const [movies, setMovies] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    apiFetch('/movies').then(({ data }) => setMovies(data || []))
      .catch(err => console.error('Lỗi tải phim:', err))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <div className="loading"><div className="spinner" />Đang tải danh sách phim...</div>

  return (
    <div>
      <h1 className="section-title">🎬 Phim đang chiếu</h1>
      <p className="section-subtitle">Chọn phim bạn muốn xem để đặt vé</p>
      <div className="grid-3">
        {movies.map(movie => (
          <div key={movie.id} className="card movie-card" onClick={() => onSelectMovie(movie)}>
            {movie.posterUrl ? (
              <img src={movie.posterUrl} alt={movie.title} className="movie-poster"
                onError={e => { e.target.style.display = 'none'; e.target.nextSibling && (e.target.nextSibling.style.display = 'flex') }} />
            ) : null}
            <div className="movie-poster-placeholder" style={movie.posterUrl ? { display: 'none' } : {}}>🎬</div>
            <h3 className="movie-title">{movie.title}</h3>
            <div className="movie-meta">
              <span className="tag tag-genre">{movie.genre}</span>
              <span className="tag tag-duration">{movie.duration} phút</span>
            </div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: 1.5 }}>
              {movie.description?.substring(0, 100)}{movie.description?.length > 100 ? '...' : ''}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
