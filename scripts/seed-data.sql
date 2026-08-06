-- ============================================
-- Seed Data for Movie Ticket Booking System
-- 15 phim đa thể loại + dữ liệu mẫu để test AI Recommender
-- ============================================

-- ============================================
-- 1. SEED MOVIES (movie_db) — 15 phim đa dạng thể loại
-- ============================================
USE movie_db;

INSERT INTO movies (id, title, genre, duration, poster_url, description) VALUES
    ('movie-001', 'Avengers: Endgame', 'Hành động, Khoa học viễn tưởng', 181,
     'https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg',
     'Sau sự kiện tàn khốc từ Avengers: Infinity War, vũ trụ đang ở trong đống đổ nát. Với sự giúp đỡ của các đồng minh còn lại, biệt đội Avengers tập hợp một lần nữa để đảo ngược hành động của Thanos và khôi phục trật tự cho vũ trụ. Cuộc chiến cuối cùng với sức mạnh siêu nhiên và công nghệ tương lai.'),

    ('movie-002', 'Spider-Man: No Way Home', 'Hành động, Khoa học viễn tưởng', 148,
     'https://image.tmdb.org/t/p/w500/1g0dhYtq4irTY1GPXvft6k4YLjm.jpg',
     'Peter Parker nhờ Doctor Strange giúp đỡ khi danh tính của anh bị tiết lộ. Khi phép thuật trục trặc, những kẻ thù nguy hiểm từ các chiều không gian khác bắt đầu xuất hiện. Một cuộc phiêu lưu đa vũ trụ với các siêu anh hùng và sức mạnh siêu nhiên.'),

    ('movie-003', 'Dune: Part Two', 'Khoa học viễn tưởng, Phiêu lưu', 166,
     'https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nez7S.jpg',
     'Paul Atreides hợp nhất với Chani và người Fremen trong khi trên con đường trả thù chống lại những kẻ âm mưu đã phá hủy gia đình anh. Hành tinh sa mạc Arrakis với những trận chiến vĩ đại, chính trị vũ trụ và lời tiên tri cổ đại.'),

    ('movie-004', 'Oppenheimer', 'Tâm lý, Lịch sử', 180,
     'https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg',
     'Câu chuyện về nhà vật lý lý thuyết Mỹ J. Robert Oppenheimer và vai trò của ông trong việc phát triển bom nguyên tử. Một bộ phim tâm lý lịch sử về sự đấu tranh nội tâm, đạo đức khoa học và hậu quả của chiến tranh hạt nhân.'),

    ('movie-005', 'Inside Out 2', 'Hoạt hình, Hài hước', 96,
     'https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg',
     'Riley bước vào tuổi thiếu niên và bộ Tư Lệnh phải đối mặt với những cảm xúc mới xuất hiện bất ngờ. Một câu chuyện hoạt hình hài hước đầy cảm xúc về sự trưởng thành và khám phá bản thân.'),

    ('movie-006', 'Parasite', 'Tâm lý, Hài đen', 132,
     'https://image.tmdb.org/t/p/w500/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg',
     'Gia đình nghèo Kim từng bước xâm nhập vào gia đình giàu có Park bằng cách giả mạo danh tính. Một bộ phim tâm lý xã hội sâu sắc về phân cách giai cấp, sự bất bình đẳng và bản chất con người trong xã hội hiện đại.'),

    ('movie-007', 'The Dark Knight', 'Hành động, Tội phạm', 152,
     'https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911kpUpRVQIN3hm.jpg',
     'Batman phải đối mặt với Joker - kẻ phá hoại thiên tài muốn nhấn chìm Gotham trong hỗn loạn. Một bộ phim hành động tội phạm đen tối về công lý, sự hy sinh và ranh giới mong manh giữa anh hùng và ác nhân.'),

    ('movie-008', 'Your Name', 'Hoạt hình, Lãng mạn', 106,
     'https://image.tmdb.org/t/p/w500/q719jXXEhI5qQxvTgPmSKM94Tka.jpg',
     'Hai thiếu niên xa lạ phát hiện họ hoán đổi thân xác trong giấc mơ. Một câu chuyện hoạt hình lãng mạn đầy kỳ ảo về tình yêu vượt thời gian, kết nối tâm hồn và số phận định mệnh giữa thành phố và nông thôn Nhật Bản.'),

    ('movie-009', 'Interstellar', 'Khoa học viễn tưởng, Tâm lý', 169,
     'https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg',
     'Một nhóm nhà thám hiểm du hành qua lỗ sâu trong vũ trụ để tìm kiếm ngôi nhà mới cho nhân loại đang trên bờ tuyệt chủng. Bộ phim khoa học viễn tưởng tâm lý về tình cha con, thời gian tương đối và sự sinh tồn của loài người.'),

    ('movie-010', 'Spirited Away', 'Hoạt hình, Phiêu lưu', 125,
     'https://image.tmdb.org/t/p/w500/39wmItIWsg5sZMyRUHLkWBcuVCM.jpg',
     'Cô bé Chihiro lạc vào thế giới thần linh kỳ bí và phải làm việc tại nhà tắm của phù thủy Yubaba để cứu cha mẹ. Một kiệt tác hoạt hình phiêu lưu kỳ ảo về lòng dũng cảm, sự trưởng thành và văn hóa tâm linh Nhật Bản.'),

    ('movie-011', 'The Shawshank Redemption', 'Tâm lý, Tội phạm', 142,
     'https://image.tmdb.org/t/p/w500/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg',
     'Andy Dufresne, một ngân hàng viên bị kết án oan giết vợ, tìm cách sinh tồn và giữ hy vọng trong nhà tù Shawshank khắc nghiệt. Bộ phim tâm lý tội phạm cảm động về tình bạn, sự tự do và sức mạnh của hy vọng.'),

    ('movie-012', 'Inception', 'Hành động, Khoa học viễn tưởng, Tâm lý', 148,
     'https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg',
     'Dom Cobb là tên trộm chuyên đánh cắp bí mật từ tiềm thức con người khi họ ngủ mơ. Một bộ phim hành động khoa học viễn tưởng về thế giới giấc mơ nhiều tầng, ký ức và ranh giới giữa thực tại và ảo giác.'),

    ('movie-013', 'Coco', 'Hoạt hình, Gia đình, Âm nhạc', 105,
     'https://image.tmdb.org/t/p/w500/gGEsBPAijhVUFoiNpgZXqRVWJt2.jpg',
     'Cậu bé Miguel mơ ước trở thành nhạc sĩ nhưng gia đình cấm âm nhạc. Cậu vô tình lạc vào Thế Giới Người Chết và khám phá bí mật gia tộc. Bộ phim hoạt hình âm nhạc đầy màu sắc về gia đình, truyền thống và đam mê nghệ thuật.'),

    ('movie-014', 'Joker', 'Tâm lý, Tội phạm', 122,
     'https://image.tmdb.org/t/p/w500/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg',
     'Arthur Fleck, một diễn viên hài thất bại sống ở Gotham, dần dần sa vào điên loạn và trở thành kẻ tội phạm khét tiếng Joker. Bộ phim tâm lý tội phạm u ám về sự cô đơn, bệnh tâm thần và sự sụp đổ của xã hội.'),

    ('movie-015', 'Frozen II', 'Hoạt hình, Phiêu lưu, Gia đình', 103,
     'https://image.tmdb.org/t/p/w500/pjeMs3yqRmFL3giJy4PMXWZTTPa.jpg',
     'Elsa nghe thấy tiếng gọi bí ẩn từ vùng rừng phía bắc và cùng Anna, Kristoff, Olaf lên đường khám phá nguồn gốc sức mạnh của mình. Bộ phim hoạt hình phiêu lưu gia đình về tình chị em, sự dũng cảm và khám phá bản thân.')
ON DUPLICATE KEY UPDATE title = VALUES(title), genre = VALUES(genre), description = VALUES(description);

-- ============================================
-- 2. SEED SHOWTIMES (movie_db)
-- ============================================

INSERT INTO showtimes (id, movie_id, hall, start_time, price) VALUES
    ('showtime-001', 'movie-001', 'Phòng A', '2026-04-20 10:00:00', 100000),
    ('showtime-002', 'movie-001', 'Phòng A', '2026-04-20 14:00:00', 120000),
    ('showtime-003', 'movie-001', 'Phòng B', '2026-04-20 19:00:00', 150000),
    ('showtime-004', 'movie-002', 'Phòng A', '2026-04-20 10:30:00', 100000),
    ('showtime-005', 'movie-002', 'Phòng B', '2026-04-20 15:00:00', 120000),
    ('showtime-006', 'movie-003', 'Phòng C', '2026-04-20 11:00:00', 130000),
    ('showtime-007', 'movie-003', 'Phòng C', '2026-04-20 18:00:00', 150000),
    ('showtime-008', 'movie-004', 'Phòng B', '2026-04-20 09:00:00', 110000),
    ('showtime-009', 'movie-004', 'Phòng A', '2026-04-20 20:00:00', 160000),
    ('showtime-010', 'movie-005', 'Phòng C', '2026-04-20 09:30:00', 80000),
    ('showtime-011', 'movie-005', 'Phòng A', '2026-04-20 13:00:00', 90000),
    ('showtime-012', 'movie-006', 'Phòng B', '2026-04-21 10:00:00', 110000),
    ('showtime-013', 'movie-007', 'Phòng A', '2026-04-21 14:00:00', 120000),
    ('showtime-014', 'movie-008', 'Phòng C', '2026-04-21 16:00:00', 100000),
    ('showtime-015', 'movie-009', 'Phòng B', '2026-04-21 19:00:00', 150000),
    ('showtime-016', 'movie-010', 'Phòng C', '2026-04-22 10:00:00', 90000),
    ('showtime-017', 'movie-011', 'Phòng A', '2026-04-22 14:00:00', 100000),
    ('showtime-018', 'movie-012', 'Phòng B', '2026-04-22 18:00:00', 140000),
    ('showtime-019', 'movie-013', 'Phòng C', '2026-04-22 10:30:00', 80000),
    ('showtime-020', 'movie-014', 'Phòng A', '2026-04-22 20:00:00', 130000),
    ('showtime-021', 'movie-015', 'Phòng C', '2026-04-23 09:00:00', 80000)
ON DUPLICATE KEY UPDATE hall = VALUES(hall);

-- ============================================
-- 3. SEED SEATS (seat_db)
-- ============================================
USE seat_db;

INSERT IGNORE INTO seats (id, showtime_id, seat_number, seat_row, status, booking_id)
SELECT
    CONCAT('seat-', st.id, '-', r.row_label, c.col_num) AS id,
    st.id AS showtime_id,
    CONCAT(r.row_label, c.col_num) AS seat_number,
    r.row_label AS seat_row,
    'AVAILABLE' AS status,
    NULL AS booking_id
FROM movie_db.showtimes st
CROSS JOIN (
    SELECT 'A' AS row_label
    UNION ALL SELECT 'B'
    UNION ALL SELECT 'C'
    UNION ALL SELECT 'D'
    UNION ALL SELECT 'E'
) r
CROSS JOIN (
    SELECT 1 AS col_num
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
    UNION ALL SELECT 5
    UNION ALL SELECT 6
    UNION ALL SELECT 7
    UNION ALL SELECT 8
) c;

-- ============================================
-- 4. SEED USERS (auth_db)
-- ============================================
USE auth_db;

INSERT INTO users (id, name, email, role) VALUES
    ('user-001', 'Nguyễn Văn A (Admin)', 'nguyenvana@email.com', 'ADMIN'),
    ('user-002', 'Trần Thị B', 'tranthib@email.com', 'USER'),
    ('user-003', 'Lê Văn C', 'levanc@email.com', 'USER'),
    ('user-004', 'Phạm Thị D', 'phamthid@email.com', 'USER'),
    ('user-005', 'Hoàng Văn E', 'hoangvane@email.com', 'USER')
ON DUPLICATE KEY UPDATE name = VALUES(name), role = VALUES(role);

-- ============================================
-- 5. SEED WALLETS (payment_db)
-- ============================================
USE payment_db;

INSERT INTO wallets (user_id, balance) VALUES
    ('user-001', 500000),
    ('user-002', 50000),
    ('user-003', 1000000),
    ('user-004', 200000),
    ('user-005', 3000000)
ON DUPLICATE KEY UPDATE balance = VALUES(balance);
