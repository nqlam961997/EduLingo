-- ─────────────────────────────────────────────
-- Tables
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS word_set (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT        NOT NULL,
    description   TEXT,
    cefr_level    TEXT        NOT NULL,   -- A1 A2 B1 B2 C1 C2
    difficulty    INT         NOT NULL DEFAULT 1,  -- 1-5
    display_order INT         NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS vocabulary (
    id              UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    word_set_id     UUID  NOT NULL REFERENCES word_set(id) ON DELETE CASCADE,
    word            TEXT  NOT NULL,
    pronunciation   TEXT,
    meaning         TEXT  NOT NULL,
    word_type       TEXT,
    example         TEXT,
    example_meaning TEXT,
    display_order   INT   NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_vocab_set ON vocabulary (word_set_id);

CREATE TABLE IF NOT EXISTS user_word_progress (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    vocabulary_id UUID        NOT NULL REFERENCES vocabulary(id) ON DELETE CASCADE,
    mastered      BOOLEAN     NOT NULL DEFAULT false,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, vocabulary_id)
);

CREATE INDEX IF NOT EXISTS idx_word_progress_user ON user_word_progress (user_id);

-- ─────────────────────────────────────────────
-- Seed data
-- ─────────────────────────────────────────────

DO $$
DECLARE
    sid UUID;
BEGIN

-- ═══ A1 ═══════════════════════════════════════

-- Set 1: Lời chào hỏi
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000001-0001-0000-0000-000000000000','Lời chào hỏi','Các câu chào hỏi & giao tiếp cơ bản','A1',1,1);
sid := '00000001-0001-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Hello','/həˈloʊ/','Xin chào','THÁN TỪ','Hello! How are you?','Xin chào! Bạn khỏe không?',1),
(sid,'Hi','/haɪ/','Chào (thân mật)','THÁN TỪ','Hi there!','Chào bạn!',2),
(sid,'Good morning','/ɡʊd ˈmɔːrnɪŋ/','Chào buổi sáng','CỤM TỪ','Good morning, everyone!','Chào buổi sáng mọi người!',3),
(sid,'Good evening','/ɡʊd ˈiːvnɪŋ/','Chào buổi tối','CỤM TỪ','Good evening, sir.','Chào buổi tối, thưa ông.',4),
(sid,'Goodbye','/ˌɡʊdˈbaɪ/','Tạm biệt','THÁN TỪ','Goodbye! See you tomorrow.','Tạm biệt! Hẹn gặp lại ngày mai.',5),
(sid,'Please','/pliːz/','Làm ơn / Xin','THÁN TỪ','Can I have water, please?','Cho tôi xin nước, được không?',6),
(sid,'Thank you','/θæŋk juː/','Cảm ơn','CỤM TỪ','Thank you for your help.','Cảm ơn bạn đã giúp đỡ.',7),
(sid,'Sorry','/ˈsɒri/','Xin lỗi','THÁN TỪ','Sorry, I am late.','Xin lỗi, tôi đến muộn.',8),
(sid,'You''re welcome','/jɔːr ˈwelkəm/','Không có gì','CỤM TỪ','You''re welcome!','Không có gì!',9);

-- Set 2: Số đếm 1–10
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000001-0002-0000-0000-000000000000','Số đếm 1–10','Các con số cơ bản từ 1 đến 10','A1',1,2);
sid := '00000001-0002-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'One','/wʌn/','Một','SỐ TỪ','I have one dog.','Tôi có một con chó.',1),
(sid,'Two','/tuː/','Hai','SỐ TỪ','There are two books.','Có hai quyển sách.',2),
(sid,'Three','/θriː/','Ba','SỐ TỪ','She has three sisters.','Cô ấy có ba người chị.',3),
(sid,'Four','/fɔːr/','Bốn','SỐ TỪ','We need four chairs.','Chúng ta cần bốn cái ghế.',4),
(sid,'Five','/faɪv/','Năm','SỐ TỪ','I wake up at five.','Tôi thức dậy lúc năm giờ.',5),
(sid,'Six','/sɪks/','Sáu','SỐ TỪ','The shop opens at six.','Cửa hàng mở lúc sáu giờ.',6),
(sid,'Seven','/ˈsevən/','Bảy','SỐ TỪ','There are seven days in a week.','Có bảy ngày trong một tuần.',7),
(sid,'Eight','/eɪt/','Tám','SỐ TỪ','The class starts at eight.','Lớp học bắt đầu lúc tám giờ.',8),
(sid,'Nine','/naɪn/','Chín','SỐ TỪ','My cat is nine years old.','Mèo của tôi chín tuổi.',9),
(sid,'Ten','/ten/','Mười','SỐ TỪ','I count to ten.','Tôi đếm đến mười.',10);

-- Set 3: Màu sắc
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000001-0003-0000-0000-000000000000','Màu sắc','Các màu sắc cơ bản','A1',1,3);
sid := '00000001-0003-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Red','/red/','Màu đỏ','TÍNH TỪ','She wears a red dress.','Cô ấy mặc váy đỏ.',1),
(sid,'Blue','/bluː/','Màu xanh dương','TÍNH TỪ','The sky is blue.','Bầu trời màu xanh.',2),
(sid,'Green','/ɡriːn/','Màu xanh lá','TÍNH TỪ','Grass is green.','Cỏ màu xanh lá.',3),
(sid,'Yellow','/ˈjeloʊ/','Màu vàng','TÍNH TỪ','The sun is yellow.','Mặt trời màu vàng.',4),
(sid,'Black','/blæk/','Màu đen','TÍNH TỪ','My cat is black.','Mèo của tôi màu đen.',5),
(sid,'White','/waɪt/','Màu trắng','TÍNH TỪ','Snow is white.','Tuyết màu trắng.',6),
(sid,'Orange','/ˈɔːrɪndʒ/','Màu cam','TÍNH TỪ','I like orange juice.','Tôi thích nước cam.',7),
(sid,'Pink','/pɪŋk/','Màu hồng','TÍNH TỪ','She loves pink flowers.','Cô ấy thích hoa màu hồng.',8);

-- Set 4: Ngày trong tuần
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000001-0004-0000-0000-000000000000','Ngày trong tuần','Thứ Hai đến Chủ Nhật','A1',1,4);
sid := '00000001-0004-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Monday','/ˈmʌndeɪ/','Thứ Hai','DANH TỪ','School starts on Monday.','Trường bắt đầu vào thứ Hai.',1),
(sid,'Tuesday','/ˈtjuːzdeɪ/','Thứ Ba','DANH TỪ','We have a meeting on Tuesday.','Chúng ta có cuộc họp vào thứ Ba.',2),
(sid,'Wednesday','/ˈwenzdeɪ/','Thứ Tư','DANH TỪ','It rains on Wednesday.','Trời mưa vào thứ Tư.',3),
(sid,'Thursday','/ˈθɜːrzdeɪ/','Thứ Năm','DANH TỪ','I go to the gym on Thursday.','Tôi đi tập gym vào thứ Năm.',4),
(sid,'Friday','/ˈfraɪdeɪ/','Thứ Sáu','DANH TỪ','Friday is the last workday.','Thứ Sáu là ngày làm việc cuối tuần.',5),
(sid,'Saturday','/ˈsætərdeɪ/','Thứ Bảy','DANH TỪ','We relax on Saturday.','Chúng ta thư giãn vào thứ Bảy.',6),
(sid,'Sunday','/ˈsʌndeɪ/','Chủ Nhật','DANH TỪ','Sunday is a day off.','Chủ Nhật là ngày nghỉ.',7);

-- Set 5: Gia đình
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000001-0005-0000-0000-000000000000','Thành viên gia đình','Từ vựng về các thành viên trong gia đình','A1',1,5);
sid := '00000001-0005-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Mother','/ˈmʌðər/','Mẹ','DANH TỪ','My mother is a teacher.','Mẹ tôi là giáo viên.',1),
(sid,'Father','/ˈfɑːðər/','Bố','DANH TỪ','My father works in Hanoi.','Bố tôi làm việc ở Hà Nội.',2),
(sid,'Sister','/ˈsɪstər/','Chị / Em gái','DANH TỪ','I have two sisters.','Tôi có hai người chị em gái.',3),
(sid,'Brother','/ˈbrʌðər/','Anh / Em trai','DANH TỪ','My brother is very tall.','Anh trai tôi rất cao.',4),
(sid,'Grandmother','/ˈɡrænˌmʌðər/','Bà nội / Bà ngoại','DANH TỪ','My grandmother makes good food.','Bà tôi nấu ăn rất ngon.',5),
(sid,'Grandfather','/ˈɡrænˌfɑːðər/','Ông nội / Ông ngoại','DANH TỪ','My grandfather tells great stories.','Ông tôi kể chuyện rất hay.',6),
(sid,'Son','/sʌn/','Con trai','DANH TỪ','Their son is five years old.','Con trai của họ năm tuổi.',7),
(sid,'Daughter','/ˈdɔːtər/','Con gái','DANH TỪ','Her daughter loves music.','Con gái cô ấy yêu thích âm nhạc.',8);

-- ═══ A2 ═══════════════════════════════════════

-- Set 6: Đồ ăn & thức uống
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000002-0001-0000-0000-000000000000','Đồ ăn & thức uống','Các món ăn và đồ uống thông dụng','A2',2,1);
sid := '00000002-0001-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Rice','/raɪs/','Cơm / Gạo','DANH TỪ','I eat rice every day.','Tôi ăn cơm mỗi ngày.',1),
(sid,'Bread','/bred/','Bánh mì','DANH TỪ','She eats bread for breakfast.','Cô ấy ăn bánh mì cho bữa sáng.',2),
(sid,'Chicken','/ˈtʃɪkɪn/','Thịt gà','DANH TỪ','I love fried chicken.','Tôi thích gà rán.',3),
(sid,'Fish','/fɪʃ/','Cá','DANH TỪ','We have fish for dinner.','Chúng ta ăn cá cho bữa tối.',4),
(sid,'Egg','/eɡ/','Trứng','DANH TỪ','He eats two eggs every morning.','Anh ấy ăn hai quả trứng mỗi sáng.',5),
(sid,'Milk','/mɪlk/','Sữa','DANH TỪ','Children drink milk daily.','Trẻ em uống sữa hàng ngày.',6),
(sid,'Water','/ˈwɔːtər/','Nước','DANH TỪ','Drink eight glasses of water a day.','Uống tám cốc nước mỗi ngày.',7),
(sid,'Coffee','/ˈkɒfi/','Cà phê','DANH TỪ','She drinks coffee in the morning.','Cô ấy uống cà phê buổi sáng.',8),
(sid,'Tea','/tiː/','Trà','DANH TỪ','He prefers tea over coffee.','Anh ấy thích trà hơn cà phê.',9),
(sid,'Fruit','/fruːt/','Trái cây','DANH TỪ','Eat more fruit every day.','Ăn nhiều trái cây mỗi ngày.',10);

-- Set 7: Động vật
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000002-0002-0000-0000-000000000000','Động vật','Các loài động vật phổ biến','A2',2,2);
sid := '00000002-0002-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Dog','/dɒɡ/','Chó','DANH TỪ','The dog barks loudly.','Con chó sủa to.',1),
(sid,'Cat','/kæt/','Mèo','DANH TỪ','My cat sleeps all day.','Mèo của tôi ngủ cả ngày.',2),
(sid,'Bird','/bɜːrd/','Chim','DANH TỪ','The bird sings in the tree.','Con chim hót trên cây.',3),
(sid,'Horse','/hɔːrs/','Ngựa','DANH TỪ','She rides a horse.','Cô ấy cưỡi ngựa.',4),
(sid,'Elephant','/ˈelɪfənt/','Voi','DANH TỪ','The elephant is the largest land animal.','Voi là động vật trên cạn lớn nhất.',5),
(sid,'Tiger','/ˈtaɪɡər/','Hổ','DANH TỪ','The tiger runs very fast.','Con hổ chạy rất nhanh.',6),
(sid,'Rabbit','/ˈræbɪt/','Thỏ','DANH TỪ','The rabbit eats carrots.','Con thỏ ăn cà rốt.',7),
(sid,'Cow','/kaʊ/','Bò','DANH TỪ','The cow gives us milk.','Con bò cho chúng ta sữa.',8),
(sid,'Monkey','/ˈmʌŋki/','Khỉ','DANH TỪ','The monkey climbs the tree.','Con khỉ leo cây.',9),
(sid,'Butterfly','/ˈbʌtərˌflaɪ/','Bướm','DANH TỪ','A butterfly lands on the flower.','Con bướm đậu trên bông hoa.',10);

-- Set 8: Nghề nghiệp
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000002-0003-0000-0000-000000000000','Nghề nghiệp','Các nghề nghiệp phổ biến','A2',2,3);
sid := '00000002-0003-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Doctor','/ˈdɒktər/','Bác sĩ','DANH TỪ','The doctor treats the patient.','Bác sĩ chữa bệnh cho bệnh nhân.',1),
(sid,'Teacher','/ˈtiːtʃər/','Giáo viên','DANH TỪ','The teacher explains the lesson.','Giáo viên giải thích bài học.',2),
(sid,'Engineer','/ˌendʒɪˈnɪər/','Kỹ sư','DANH TỪ','He is a software engineer.','Anh ấy là kỹ sư phần mềm.',3),
(sid,'Nurse','/nɜːrs/','Y tá','DANH TỪ','The nurse takes care of the patient.','Y tá chăm sóc bệnh nhân.',4),
(sid,'Chef','/ʃef/','Đầu bếp','DANH TỪ','The chef cooks delicious meals.','Đầu bếp nấu những bữa ăn ngon.',5),
(sid,'Driver','/ˈdraɪvər/','Tài xế','DANH TỪ','The bus driver is very careful.','Tài xế xe buýt rất cẩn thận.',6),
(sid,'Farmer','/ˈfɑːrmər/','Nông dân','DANH TỪ','The farmer grows rice.','Người nông dân trồng lúa.',7),
(sid,'Student','/ˈstjuːdənt/','Học sinh / Sinh viên','DANH TỪ','The student studies hard.','Học sinh học tập chăm chỉ.',8);

-- ═══ B1 ═══════════════════════════════════════

-- Set 9: Cảm xúc
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000003-0001-0000-0000-000000000000','Cảm xúc & tình cảm','Diễn đạt cảm xúc bằng tiếng Anh','B1',3,1);
sid := '00000003-0001-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Happy','/ˈhæpi/','Vui vẻ / Hạnh phúc','TÍNH TỪ','She feels happy today.','Cô ấy cảm thấy vui hôm nay.',1),
(sid,'Sad','/sæd/','Buồn bã','TÍNH TỪ','He looks sad after the loss.','Anh ấy trông buồn sau thất bại.',2),
(sid,'Angry','/ˈæŋɡri/','Tức giận','TÍNH TỪ','She was angry at the mistake.','Cô ấy tức giận vì lỗi đó.',3),
(sid,'Excited','/ɪkˈsaɪtɪd/','Phấn khích','TÍNH TỪ','They are excited about the trip.','Họ rất phấn khích về chuyến đi.',4),
(sid,'Nervous','/ˈnɜːrvəs/','Lo lắng / Hồi hộp','TÍNH TỪ','She feels nervous before the exam.','Cô ấy hồi hộp trước kỳ thi.',5),
(sid,'Surprised','/sərˈpraɪzd/','Ngạc nhiên','TÍNH TỪ','He was surprised by the news.','Anh ấy ngạc nhiên với tin tức đó.',6),
(sid,'Disappointed','/ˌdɪsəˈpɔɪntɪd/','Thất vọng','TÍNH TỪ','She was disappointed with the result.','Cô ấy thất vọng với kết quả.',7),
(sid,'Confident','/ˈkɒnfɪdənt/','Tự tin','TÍNH TỪ','He speaks English confidently.','Anh ấy nói tiếng Anh rất tự tin.',8);

-- Set 10: Du lịch
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000003-0002-0000-0000-000000000000','Du lịch','Từ vựng cần thiết khi đi du lịch','B1',3,2);
sid := '00000003-0002-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Airport','/ˈeərpɔːrt/','Sân bay','DANH TỪ','We arrive at the airport early.','Chúng tôi đến sân bay sớm.',1),
(sid,'Passport','/ˈpæspɔːrt/','Hộ chiếu','DANH TỪ','Don''t forget your passport.','Đừng quên hộ chiếu của bạn.',2),
(sid,'Hotel','/həʊˈtel/','Khách sạn','DANH TỪ','We stay at a five-star hotel.','Chúng tôi ở khách sạn năm sao.',3),
(sid,'Reservation','/ˌrezərˈveɪʃən/','Đặt chỗ / Đặt phòng','DANH TỪ','I made a reservation online.','Tôi đặt phòng trực tuyến.',4),
(sid,'Luggage','/ˈlʌɡɪdʒ/','Hành lý','DANH TỪ','Please check your luggage weight.','Vui lòng kiểm tra trọng lượng hành lý.',5),
(sid,'Destination','/ˌdestɪˈneɪʃən/','Điểm đến','DANH TỪ','Paris is our final destination.','Paris là điểm đến cuối cùng của chúng tôi.',6),
(sid,'Currency','/ˈkɜːrənsi/','Tiền tệ','DANH TỪ','What is the local currency here?','Tiền tệ địa phương ở đây là gì?',7),
(sid,'Itinerary','/aɪˈtɪnəreri/','Lịch trình (du lịch)','DANH TỪ','Our itinerary includes three cities.','Lịch trình của chúng tôi bao gồm ba thành phố.',8);

-- ═══ B2 ═══════════════════════════════════════

-- Set 11: Môi trường
INSERT INTO word_set (id, name, description, cefr_level, difficulty, display_order)
VALUES ('00000004-0001-0000-0000-000000000000','Môi trường','Từ vựng về bảo vệ môi trường','B2',4,1);
sid := '00000004-0001-0000-0000-000000000000';
INSERT INTO vocabulary (word_set_id,word,pronunciation,meaning,word_type,example,example_meaning,display_order) VALUES
(sid,'Pollution','/pəˈluːʃən/','Ô nhiễm','DANH TỪ','Air pollution is a serious problem.','Ô nhiễm không khí là một vấn đề nghiêm trọng.',1),
(sid,'Sustainable','/səˈsteɪnəbəl/','Bền vững','TÍNH TỪ','We need sustainable energy sources.','Chúng ta cần nguồn năng lượng bền vững.',2),
(sid,'Renewable','/rɪˈnjuːəbəl/','Có thể tái tạo','TÍNH TỪ','Solar is a renewable energy.','Năng lượng mặt trời là năng lượng tái tạo.',3),
(sid,'Conservation','/ˌkɒnsərˈveɪʃən/','Bảo tồn','DANH TỪ','Wildlife conservation is important.','Bảo tồn động vật hoang dã rất quan trọng.',4),
(sid,'Emission','/ɪˈmɪʃən/','Khí thải','DANH TỪ','We must reduce carbon emissions.','Chúng ta phải giảm khí thải carbon.',5),
(sid,'Biodiversity','/ˌbaɪoʊdaɪˈvɜːrsɪti/','Đa dạng sinh học','DANH TỪ','Biodiversity is essential for ecosystems.','Đa dạng sinh học rất cần thiết cho hệ sinh thái.',6),
(sid,'Recycle','/ˌriːˈsaɪkəl/','Tái chế','ĐỘNG TỪ','Please recycle your plastic bottles.','Hãy tái chế chai nhựa của bạn.',7),
(sid,'Deforestation','/ˌdiːˌfɒrɪˈsteɪʃən/','Nạn phá rừng','DANH TỪ','Deforestation destroys natural habitats.','Phá rừng hủy hoại môi trường sống tự nhiên.',8);

END $$;
