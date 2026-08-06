-- 연동 가능 기관 마스터 데이터
SET NAMES utf8mb4;

DELETE FROM institution;

INSERT INTO institution (code, name, institution_type, business_type, login_type, product_label, display_order) VALUES
-- 시중은행
('0004', 'KB국민은행', 'BANK', 'BK', '1', '예적금 · 대출', 1),
('0007', '신한은행', 'BANK', 'BK', '1', '예적금 · 대출', 2),
('0011', 'NH농협은행', 'BANK', 'BK', '1', '예적금 · 대출', 3),
('0020', '우리은행', 'BANK', 'BK', '1', '예적금 · 대출', 4),
('0081', '하나은행', 'BANK', 'BK', '1', '예적금 · 대출', 5),
('0088', 'IBK기업은행', 'BANK', 'BK', '1', '예적금 · 대출', 6),

-- 증권
('0218', 'KB증권', 'STOCK', 'ST', '1', '주식 · 펀드', 13),
('0238', '미래에셋증권', 'STOCK', 'ST', '1', '주식 · 펀드', 14),
('0240', '삼성증권', 'STOCK', 'ST', '1', '주식 · 펀드', 16),
('0243', '한국투자증권', 'STOCK', 'ST', '1', '주식 · 펀드', 15),
('0247', 'NH투자증권', 'STOCK', 'ST', '1', '주식 · 펀드', 16);


INSERT INTO institution (code, name, institution_type, business_type, login_type, product_label, display_order, extra_field_schema) VALUES
-- 카드
('0301', 'KB국민카드', 'CARD', 'CD', '1', '카드', 17, '[{"key":"cardNo","label":"카드번호","type":"text","required":false},{"key":"cardPassword","label":"카드 비밀번호","type":"password","required":false}]'),
('0302', '현대카드', 'CARD', 'CD', '1', '카드', 18, '[{"key":"cardNo","label":"카드번호","type":"text","required":true},{"key":"cardPassword","label":"카드 비밀번호","type":"password","required":true}]'),
('0303', '삼성카드', 'CARD', 'CD', '1', '카드', 19, NULL),
('0306', '신한카드', 'CARD', 'CD', '1', '카드', 20, NULL),
('0309', '우리카드', 'CARD', 'CD', '1', '카드', 21, '[{"key":"birthDate","label":"생년월일","type":"text","required":false,"placeholder":"YYYYMMDD"}]');
