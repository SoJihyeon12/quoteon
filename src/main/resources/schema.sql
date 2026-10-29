-- =========================================
-- QuoteOn 데이터베이스 테이블 생성
-- =========================================

-- 1. 회원
CREATE TABLE IF NOT EXISTS member (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      email VARCHAR(255) NOT NULL UNIQUE,
                                      password VARCHAR(255) NOT NULL,
                                      name VARCHAR(100) NOT NULL,
                                      phone VARCHAR(20),
                                      company_name VARCHAR(255),
                                      role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER',
                                      status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 카테고리
CREATE TABLE IF NOT EXISTS category (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        name VARCHAR(100) NOT NULL,
                                        parent_id BIGINT,
                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP,

                                        CONSTRAINT fk_category_parent
                                            FOREIGN KEY (parent_id)
                                                REFERENCES category(id)
);

-- 3. 제품
CREATE TABLE IF NOT EXISTS product (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       category_id BIGINT NOT NULL,
                                       product_code VARCHAR(100) NOT NULL UNIQUE,
                                       name VARCHAR(255) NOT NULL,
                                       unit_price DECIMAL(12, 2) NOT NULL,
                                       sale_price DECIMAL(12, 2) NOT NULL,
                                       specification VARCHAR(500),
                                       description TEXT,
                                       image_path VARCHAR(500),
                                       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                           ON UPDATE CURRENT_TIMESTAMP,

                                       CONSTRAINT fk_product_category
                                           FOREIGN KEY (category_id)
                                               REFERENCES category(id)
);

-- 4. 고객
CREATE TABLE IF NOT EXISTS customer (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        member_id BIGINT NOT NULL,
                                        name VARCHAR(100) NOT NULL,
                                        customer_type VARCHAR(30) NOT NULL,
                                        phone VARCHAR(20),
                                        email VARCHAR(255),
                                        address VARCHAR(500),
                                        memo TEXT,
                                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP,

                                        CONSTRAINT fk_customer_member
                                            FOREIGN KEY (member_id)
                                                REFERENCES member(id)
);

-- 5. 견적
CREATE TABLE IF NOT EXISTS quote (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     quote_number VARCHAR(100) NOT NULL UNIQUE,
                                     member_id BIGINT NOT NULL,
                                     customer_id BIGINT NOT NULL,
                                     supply_amount DECIMAL(14, 2) NOT NULL DEFAULT 0,
                                     vat_amount DECIMAL(14, 2) NOT NULL DEFAULT 0,
                                     total_amount DECIMAL(14, 2) NOT NULL DEFAULT 0,
                                     status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                                     quote_date DATE NOT NULL,
                                     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_quote_member
                                         FOREIGN KEY (member_id)
                                             REFERENCES member(id),

                                     CONSTRAINT fk_quote_customer
                                         FOREIGN KEY (customer_id)
                                             REFERENCES customer(id)
);

-- 6. 견적 항목
CREATE TABLE IF NOT EXISTS quote_item (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          quote_id BIGINT NOT NULL,
                                          product_id BIGINT,
                                          product_name VARCHAR(255) NOT NULL,
                                          product_code VARCHAR(100) NOT NULL,
                                          sale_price DECIMAL(12, 2) NOT NULL,
                                          quantity INT NOT NULL,
                                          amount DECIMAL(14, 2) NOT NULL,

                                          CONSTRAINT fk_quote_item_quote
                                              FOREIGN KEY (quote_id)
                                                  REFERENCES quote(id)
                                                  ON DELETE CASCADE,

                                          CONSTRAINT fk_quote_item_product
                                              FOREIGN KEY (product_id)
                                                  REFERENCES product(id)
);

-- 7. 견적 이메일 발송 이력
CREATE TABLE IF NOT EXISTS quote_email_history (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   quote_id BIGINT NOT NULL,
                                                   recipient_email VARCHAR(255) NOT NULL,
                                                   subject VARCHAR(255),
                                                   status VARCHAR(20) NOT NULL,
                                                   failure_reason TEXT,
                                                   sent_at DATETIME,
                                                   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                                   CONSTRAINT fk_quote_email_history_quote
                                                       FOREIGN KEY (quote_id)
                                                           REFERENCES quote(id)
                                                           ON DELETE CASCADE
);