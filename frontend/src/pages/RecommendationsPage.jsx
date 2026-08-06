import { useState, useEffect, useRef } from 'react'
import { apiFetch } from '../utils/api'

export default function RecommendationsPage({ onSelectMovie }) {
  const [topPicks, setTopPicks] = useState([])
  const [genreSections, setGenreSections] = useState([])
  const [userGenres, setUserGenres] = useState([])
  const [allMovies, setAllMovies] = useState([])
  const [hasHistory, setHasHistory] = useState(null)
  const [loading, setLoading] = useState(true)

  function normalizeGenres(rec, movieDetail) {
    if (Array.isArray(rec.genres) && rec.genres.length > 0) return rec.genres
    if (typeof movieDetail?.genre === 'string' && movieDetail.genre.trim().length > 0) {
      return movieDetail.genre.split(',').map(g => g.trim()).filter(Boolean)
    }
    return []
  }

  function enrichMovie(rec, mMap) {
    const movieDetail = mMap.get(rec.movieId)
    return {
      ...rec,
      title: movieDetail?.title || rec.title,
      description: movieDetail?.description || rec.description || '',
      posterUrl: movieDetail?.posterUrl || rec.posterUrl || '',
      genre: movieDetail?.genre || rec.genre || '',
      duration: movieDetail?.duration || rec.duration,
      genres: normalizeGenres(rec, movieDetail),
    }
  }

  async function handleSelectRecommendation(rec) {
    onSelectMovie({
      id: rec.movieId,
      title: rec.title,
      genre: rec.genre || rec.genres?.join(', '),
      description: rec.description || '',
      posterUrl: rec.posterUrl,
      duration: rec.duration,
    })
  }

  useEffect(() => {
    loadData()
  }, [])

  async function loadData() {
    setLoading(true)
    try {
      const recRes = await apiFetch('/recommendations/grouped?limit=10')

      const moviesRes = await apiFetch('/movies')
      const movies = moviesRes.ok ? (moviesRes.data || []) : []
      const mMap = new Map(movies.map(movie => [movie.id, movie]))

      if (recRes.ok && recRes.data?.topPicks?.length > 0) {
        setHasHistory(true)

        const enrichedTopPicks = recRes.data.topPicks.map(rec => enrichMovie(rec, mMap))
        setTopPicks(enrichedTopPicks)

        const enrichedGenreSections = (recRes.data.genreSections || []).map(section => ({
          genre: section.genre,
          movies: section.movies.map(rec => enrichMovie(rec, mMap)),
        }))
        setGenreSections(enrichedGenreSections)
        setUserGenres(recRes.data.userGenres || [])
      } else {
        setHasHistory(false)
        setAllMovies(movies)
      }
    } catch (err) {
      console.error('Lỗi tải gợi ý:', err)
      setHasHistory(false)
      const moviesRes = await apiFetch('/movies')
      if (moviesRes.ok) setAllMovies(moviesRes.data || [])
    }
    setLoading(false)
  }

  if (loading) {
    return <div className="loading"><div className="spinner" />Đang phân tích sở thích của bạn...</div>
  }

  if (hasHistory && topPicks.length > 0) {
    return (
      <div className="rec-page">
        <div className="rec-hero">
          <div className="rec-hero-glow" />
          <h1 className="rec-hero-title">
            <span className="rec-hero-icon">🤖</span>
            Gợi ý phim cho bạn
          </h1>
          <p className="rec-hero-subtitle">
            AI phân tích lịch sử đặt vé và gợi ý phim phù hợp nhất với sở thích của bạn
          </p>
          <div className="rec-hero-tags">
            {userGenres.map((g, i) => (
              <span key={i} className="rec-hero-tag">{g}</span>
            ))}
          </div>
        </div>

        <div className="rec-algo-info">
          <span>🧠</span>
          <span>
            Sử dụng <strong style={{ color: 'var(--primary-light)' }}>all-MiniLM-L6-v2</strong> —
            Điểm = <strong>60% Cosine Similarity</strong> (mô tả) + <strong>40% Jaccard Similarity</strong> (thể loại)
          </span>
        </div>

        <div className="rec-section">
          <div className="rec-section-header">
            <h2 className="rec-section-title">
              <span className="rec-section-icon">✨</span>
              Gợi ý cho bạn
            </h2>
            <span className="rec-section-badge">AI Top Picks</span>
          </div>
          <p className="rec-section-desc">
            Top phim có điểm tương đồng cao nhất, kết hợp nhiều thể loại
          </p>

          <HorizontalScroll>
            {topPicks.map((rec, idx) => (
              <MovieCard
                key={rec.movieId}
                rec={rec}
                idx={idx}
                rank={idx + 1}
                isTopPick
                onClick={() => handleSelectRecommendation(rec)}
              />
            ))}
          </HorizontalScroll>
        </div>

        {genreSections.map((section, sIdx) => (
          <div key={section.genre} className="rec-section">
            <div className="rec-section-header">
              <h2 className="rec-section-title">
                <span className="rec-section-icon">{getGenreEmoji(section.genre)}</span>
                Vì bạn thích {section.genre}
              </h2>
              <span className="rec-section-count">{section.movies.length} phim</span>
            </div>
            <p className="rec-section-desc">
              Phim thuộc thể loại <strong>{section.genre}</strong> được AI đánh giá tương đồng với phim bạn đã xem
            </p>

            <HorizontalScroll>
              {section.movies.map((rec, idx) => (
                <MovieCard
                  key={rec.movieId}
                  rec={rec}
                  idx={idx}
                  onClick={() => handleSelectRecommendation(rec)}
                />
              ))}
            </HorizontalScroll>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="rec-page">
      <div className="rec-hero" style={{ '--hero-accent': 'var(--accent)' }}>
        <div className="rec-hero-glow" style={{ background: 'radial-gradient(ellipse at 50% 0%, rgba(245,158,11,0.15) 0%, transparent 70%)' }} />
        <h1 className="rec-hero-title">
          <span className="rec-hero-icon">🔥</span>
          Phim phổ biến
        </h1>
        <p className="rec-hero-subtitle">
          Bạn chưa đặt vé phim nào. Hãy khám phá các phim đang chiếu và đặt vé để AI gợi ý phim!
        </p>
      </div>

      <div className="rec-algo-info" style={{ borderColor: 'rgba(245,158,11,0.2)', background: 'rgba(245,158,11,0.06)' }}>
        <span>💡</span>
        <span>Đặt vé xem phim để AI bắt đầu gợi ý phim phù hợp với sở thích của bạn!</span>
      </div>

      <div className="grid-3">
        {allMovies.map(movie => (
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

function HorizontalScroll({ children }) {
  const scrollRef = useRef(null)
  const [canScrollLeft, setCanScrollLeft] = useState(false)
  const [canScrollRight, setCanScrollRight] = useState(false)

  function checkScroll() {
    const el = scrollRef.current
    if (!el) return
    setCanScrollLeft(el.scrollLeft > 10)
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 10)
  }

  useEffect(() => {
    checkScroll()
    const el = scrollRef.current
    if (el) el.addEventListener('scroll', checkScroll, { passive: true })
    window.addEventListener('resize', checkScroll)
    return () => {
      if (el) el.removeEventListener('scroll', checkScroll)
      window.removeEventListener('resize', checkScroll)
    }
  }, [children])

  function scroll(dir) {
    const el = scrollRef.current
    if (!el) return
    el.scrollBy({ left: dir * 340, behavior: 'smooth' })
  }

  return (
    <div className="hscroll-wrapper">
      {canScrollLeft && (
        <button className="hscroll-arrow hscroll-arrow-left" onClick={() => scroll(-1)}>‹</button>
      )}
      <div className="hscroll-track" ref={scrollRef}>
        {children}
      </div>
      {canScrollRight && (
        <button className="hscroll-arrow hscroll-arrow-right" onClick={() => scroll(1)}>›</button>
      )}
    </div>
  )
}

function MovieCard({ rec, idx, rank, isTopPick, onClick }) {
  return (
    <div className={`rec-card ${isTopPick ? 'rec-card--top' : ''}`} onClick={onClick}>
      {rank && (
        <div className={`rec-card-rank ${rank <= 3 ? 'rec-card-rank--gold' : ''}`}>
          #{rank}
        </div>
      )}

      <div className="rec-card-poster-wrap">
        <div className="rec-card-poster-placeholder" style={{
          background: `linear-gradient(135deg, hsl(${(idx * 47 + 200) % 360}, 55%, 25%), hsl(${(idx * 47 + 230) % 360}, 40%, 15%))`,
          display: rec.posterUrl ? 'none' : 'flex',
        }}>
          🎬
        </div>
        {rec.posterUrl ? (
          <img
            src={rec.posterUrl}
            alt={rec.title}
            className="rec-card-poster"
            onError={e => {
              e.currentTarget.style.display = 'none'
              const fallback = e.currentTarget.previousSibling
              if (fallback) fallback.style.display = 'flex'
            }}
          />
        ) : null}
        <div className="rec-card-overlay">
          <span>Đặt vé →</span>
        </div>
      </div>

      <div className="rec-card-content">
        <h3 className="rec-card-title">{rec.title}</h3>

        <div className="rec-card-genres">
          {rec.genres?.map((g, i) => (
            <span key={i} className="tag tag-genre">{g}</span>
          ))}
          {rec.duration ? <span className="tag tag-duration">{rec.duration} phút</span> : null}
        </div>

        <p className="rec-card-desc">
          {rec.description
            ? `${rec.description.substring(0, 90)}${rec.description.length > 90 ? '...' : ''}`
            : 'Phim hiện chưa có mô tả chi tiết.'}
        </p>


      </div>
    </div>
  )
}

function getGenreEmoji(genre) {
  const g = genre.toLowerCase()
  if (g.includes('hành động') || g.includes('action')) return '💥'
  if (g.includes('kinh dị') || g.includes('horror')) return '👻'
  if (g.includes('tâm lý') || g.includes('psycho')) return '🧠'
  if (g.includes('hài') || g.includes('comedy')) return '😂'
  if (g.includes('lãng mạn') || g.includes('romance')) return '💕'
  if (g.includes('viễn tưởng') || g.includes('sci')) return '🚀'
  if (g.includes('hoạt hình') || g.includes('anim')) return '🎨'
  if (g.includes('phiêu lưu') || g.includes('adventure')) return '🗺️'
  if (g.includes('drama') || g.includes('chính kịch')) return '🎭'
  if (g.includes('tội phạm') || g.includes('crime')) return '🔫'
  if (g.includes('chiến tranh') || g.includes('war')) return '⚔️'
  if (g.includes('tài liệu') || g.includes('document')) return '📽️'
  if (g.includes('gia đình') || g.includes('family')) return '👨‍👩‍👧‍👦'
  if (g.includes('mystery') || g.includes('bí ẩn')) return '🔍'
  if (g.includes('thriller')) return '😰'
  if (g.includes('fantasy') || g.includes('giả tưởng')) return '🧙'
  return '🎬'
}
