USE master;
GO

IF DB_ID(N'gymfit') IS NOT NULL
BEGIN
    ALTER DATABASE gymfit SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE gymfit;
END;
GO

CREATE DATABASE gymfit;
GO

ALTER DATABASE gymfit COLLATE Vietnamese_100_CI_AI;
GO

USE gymfit;
GO

CREATE TABLE branch (
                        id BIGINT IDENTITY(1,1) NOT NULL,
                        code VARCHAR(20) NOT NULL,
                        name NVARCHAR(120) NOT NULL,
                        address NVARCHAR(255) NOT NULL,
                        phone VARCHAR(20) NULL,
                        status VARCHAR(20) NOT NULL,
                        timezone VARCHAR(60) NOT NULL,
                        created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_branch_created_at DEFAULT SYSUTCDATETIME(),
                        updated_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_branch_updated_at DEFAULT SYSUTCDATETIME(),

                        CONSTRAINT PK_branch PRIMARY KEY (id),
                        CONSTRAINT UQ_branch_code UNIQUE (code),
                        CONSTRAINT CK_branch_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
GO

CREATE INDEX IX_branch_status
    ON branch(status);
GO

CREATE TABLE branch_service_config (
                                       branch_id BIGINT NOT NULL,
                                       service_code VARCHAR(20) NOT NULL,
                                       booking_duration_minutes INT NOT NULL,
                                       capacity INT NOT NULL,
                                       booking_enabled BIT NOT NULL CONSTRAINT DF_branch_service_booking_enabled DEFAULT 1,

                                       CONSTRAINT PK_branch_service_config PRIMARY KEY (branch_id, service_code),

                                       CONSTRAINT FK_branch_service_config_branch
                                           FOREIGN KEY (branch_id)
                                               REFERENCES branch(id),

                                       CONSTRAINT CK_branch_service_config_service
                                           CHECK (service_code IN ('GYM', 'BOXING', 'PICKLEBALL')),

                                       CONSTRAINT CK_branch_service_config_duration
                                           CHECK (booking_duration_minutes BETWEEN 30 AND 120),

                                       CONSTRAINT CK_branch_service_config_capacity
                                           CHECK (capacity >= 1)
);
GO

CREATE TABLE branch_operating_hour (
                                       id BIGINT IDENTITY(1,1) NOT NULL,
                                       branch_id BIGINT NOT NULL,
                                       day_of_week INT NOT NULL,
                                       open_time TIME(0) NOT NULL,
                                       close_time TIME(0) NOT NULL,

                                       CONSTRAINT PK_branch_operating_hour PRIMARY KEY (id),

                                       CONSTRAINT FK_branch_operating_hour_branch
                                           FOREIGN KEY (branch_id)
                                               REFERENCES branch(id),

                                       CONSTRAINT UQ_branch_operating_hour
                                           UNIQUE (branch_id, day_of_week),

                                       CONSTRAINT CK_branch_operating_hour_day
                                           CHECK (day_of_week BETWEEN 1 AND 7),

                                       CONSTRAINT CK_branch_operating_hour_time
                                           CHECK (open_time < close_time)
);
GO

CREATE TABLE facility (
                          id BIGINT IDENTITY(1,1) NOT NULL,
                          branch_id BIGINT NOT NULL,
                          service_code VARCHAR(20) NOT NULL,
                          facility_type VARCHAR(30) NOT NULL,
                          name NVARCHAR(120) NOT NULL,
                          capacity INT NOT NULL,
                          status VARCHAR(20) NOT NULL,
                          created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_facility_created_at DEFAULT SYSUTCDATETIME(),
                          updated_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_facility_updated_at DEFAULT SYSUTCDATETIME(),

                          CONSTRAINT PK_facility PRIMARY KEY (id),

                          CONSTRAINT FK_facility_branch
                              FOREIGN KEY (branch_id)
                                  REFERENCES branch(id),

                          CONSTRAINT UQ_facility_branch_name
                              UNIQUE (branch_id, name),

                          CONSTRAINT CK_facility_service
                              CHECK (service_code IN ('GYM', 'BOXING', 'PICKLEBALL')),

                          CONSTRAINT CK_facility_type
                              CHECK (facility_type IN (
                                                       'GYM_AREA',
                                                       'BOXING_ROOM',
                                                       'PICKLEBALL_COURT'
                                  )),

                          CONSTRAINT CK_facility_capacity
                              CHECK (capacity >= 1),

                          CONSTRAINT CK_facility_status
                              CHECK (status IN (
                                                'ACTIVE',
                                                'MAINTENANCE',
                                                'INACTIVE'
                                  )),

                          CONSTRAINT CK_facility_service_type
                              CHECK (
                                  (service_code = 'GYM' AND facility_type = 'GYM_AREA')
                                      OR
                                  (service_code = 'BOXING' AND facility_type = 'BOXING_ROOM')
                                      OR
                                  (service_code = 'PICKLEBALL' AND facility_type = 'PICKLEBALL_COURT')
                                  )
);
GO

CREATE INDEX IX_facility_branch_service_status
    ON facility(branch_id, service_code, status);
GO

CREATE TABLE member (
                        id BIGINT IDENTITY(1,1) NOT NULL,
                        member_code VARCHAR(30) NOT NULL,
                        full_name NVARCHAR(120) NOT NULL,
                        phone VARCHAR(20) NOT NULL,
                        email VARCHAR(150) NULL,
                        home_branch_id BIGINT NOT NULL,
                        date_of_birth DATE NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_member_created_at DEFAULT SYSUTCDATETIME(),
                        updated_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_member_updated_at DEFAULT SYSUTCDATETIME(),

                        CONSTRAINT PK_member PRIMARY KEY (id),

                        CONSTRAINT UQ_member_code
                            UNIQUE (member_code),

                        CONSTRAINT UQ_member_phone
                            UNIQUE (phone),

                        CONSTRAINT FK_member_home_branch
                            FOREIGN KEY (home_branch_id)
                                REFERENCES branch(id),

                        CONSTRAINT CK_member_status
                            CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
GO

CREATE UNIQUE INDEX UX_member_email
    ON member(email)
    WHERE email IS NOT NULL;
GO

CREATE INDEX IX_member_home_branch_status
    ON member(home_branch_id, status);
GO

CREATE INDEX IX_member_full_name
    ON member(full_name);
GO

CREATE TABLE app_user (
                          id BIGINT IDENTITY(1,1) NOT NULL,
                          full_name NVARCHAR(120) NOT NULL,
                          email VARCHAR(150) NOT NULL,
                          password_hash VARCHAR(100) NOT NULL,
                          role_code VARCHAR(30) NOT NULL,
                          status VARCHAR(20) NOT NULL,
                          branch_id BIGINT NULL,
                          member_id BIGINT NULL,
                          must_change_password BIT NOT NULL
                              CONSTRAINT DF_app_user_must_change_password DEFAULT 0,
                          created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_app_user_created_at DEFAULT SYSUTCDATETIME(),
                          updated_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_app_user_updated_at DEFAULT SYSUTCDATETIME(),

                          CONSTRAINT PK_app_user PRIMARY KEY (id),

                          CONSTRAINT UQ_app_user_email
                              UNIQUE (email),

                          CONSTRAINT FK_app_user_branch
                              FOREIGN KEY (branch_id)
                                  REFERENCES branch(id),

                          CONSTRAINT FK_app_user_member
                              FOREIGN KEY (member_id)
                                  REFERENCES member(id),

                          CONSTRAINT CK_app_user_role
                              CHECK (role_code IN (
                                                   'ADMIN',
                                                   'BRANCH_MANAGER',
                                                   'MEMBER'
                                  )),

                          CONSTRAINT CK_app_user_status
                              CHECK (status IN (
                                                'ACTIVE',
                                                'LOCKED',
                                                'DISABLED'
                                  )),

                          CONSTRAINT CK_app_user_scope
                              CHECK (
                                  (
                                      role_code = 'ADMIN'
                                          AND branch_id IS NULL
                                          AND member_id IS NULL
                                      )
                                      OR
                                  (
                                      role_code = 'BRANCH_MANAGER'
                                          AND branch_id IS NOT NULL
                                          AND member_id IS NULL
                                      )
                                      OR
                                  (
                                      role_code = 'MEMBER'
                                          AND branch_id IS NULL
                                          AND member_id IS NOT NULL
                                      )
                                  )
);
GO

CREATE UNIQUE INDEX UX_app_user_member
    ON app_user(member_id)
    WHERE member_id IS NOT NULL;
GO

CREATE INDEX IX_app_user_branch
    ON app_user(branch_id)
    WHERE branch_id IS NOT NULL;
GO

CREATE INDEX IX_app_user_role_status
    ON app_user(role_code, status);
GO

CREATE TABLE membership_plan (
                                 id BIGINT IDENTITY(1,1) NOT NULL,
                                 branch_id BIGINT NOT NULL,
                                 plan_code VARCHAR(40) NOT NULL,
                                 name NVARCHAR(120) NOT NULL,
                                 tier VARCHAR(20) NOT NULL,
                                 duration_days INT NOT NULL,
                                 price DECIMAL(18,2) NOT NULL,
                                 description NVARCHAR(500) NULL,
                                 status VARCHAR(20) NOT NULL,
                                 created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_membership_plan_created_at DEFAULT SYSUTCDATETIME(),
                                 updated_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_membership_plan_updated_at DEFAULT SYSUTCDATETIME(),

                                 CONSTRAINT PK_membership_plan PRIMARY KEY (id),

                                 CONSTRAINT FK_membership_plan_branch
                                     FOREIGN KEY (branch_id)
                                         REFERENCES branch(id),

                                 CONSTRAINT UQ_membership_plan_code
                                     UNIQUE (plan_code),

                                 CONSTRAINT CK_membership_plan_tier
                                     CHECK (tier IN (
                                                     'BASIC',
                                                     'STANDARD',
                                                     'PREMIUM'
                                         )),

                                 CONSTRAINT CK_membership_plan_duration
                                     CHECK (duration_days >= 1),

                                 CONSTRAINT CK_membership_plan_price
                                     CHECK (price >= 0),

                                 CONSTRAINT CK_membership_plan_status
                                     CHECK (status IN (
                                                       'ACTIVE',
                                                       'INACTIVE'
                                         ))
);
GO

CREATE INDEX IX_membership_plan_branch_status
    ON membership_plan(branch_id, status);
GO

CREATE TABLE plan_service (
                              plan_id BIGINT NOT NULL,
                              service_code VARCHAR(20) NOT NULL,

                              CONSTRAINT PK_plan_service
                                  PRIMARY KEY (plan_id, service_code),

                              CONSTRAINT FK_plan_service_plan
                                  FOREIGN KEY (plan_id)
                                      REFERENCES membership_plan(id),

                              CONSTRAINT CK_plan_service_code
                                  CHECK (service_code IN (
                                                          'GYM',
                                                          'BOXING',
                                                          'PICKLEBALL'
                                      ))
);
GO

CREATE TABLE product (
                         id BIGINT IDENTITY(1,1) NOT NULL,
                         sku VARCHAR(40) NOT NULL,
                         name NVARCHAR(140) NOT NULL,
                         category NVARCHAR(60) NOT NULL,
                         price DECIMAL(18,2) NOT NULL,
                         status VARCHAR(20) NOT NULL,
                         created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_product_created_at DEFAULT SYSUTCDATETIME(),
                         updated_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_product_updated_at DEFAULT SYSUTCDATETIME(),

                         CONSTRAINT PK_product PRIMARY KEY (id),

                         CONSTRAINT UQ_product_sku
                             UNIQUE (sku),

                         CONSTRAINT CK_product_price
                             CHECK (price >= 0),

                         CONSTRAINT CK_product_status
                             CHECK (status IN (
                                               'ACTIVE',
                                               'INACTIVE'
                                 ))
);
GO

CREATE INDEX IX_product_status_category
    ON product(status, category);
GO

CREATE TABLE stock_level (
                             id BIGINT IDENTITY(1,1) NOT NULL,
                             branch_id BIGINT NOT NULL,
                             product_id BIGINT NOT NULL,
                             quantity INT NOT NULL,
                             updated_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_stock_level_updated_at DEFAULT SYSUTCDATETIME(),

                             CONSTRAINT PK_stock_level PRIMARY KEY (id),

                             CONSTRAINT FK_stock_level_branch
                                 FOREIGN KEY (branch_id)
                                     REFERENCES branch(id),

                             CONSTRAINT FK_stock_level_product
                                 FOREIGN KEY (product_id)
                                     REFERENCES product(id),

                             CONSTRAINT UQ_stock_level_branch_product
                                 UNIQUE (branch_id, product_id),

                             CONSTRAINT CK_stock_level_quantity
                                 CHECK (quantity >= 0)
);
GO

CREATE INDEX IX_stock_level_branch
    ON stock_level(branch_id, product_id);
GO

CREATE TABLE stock_movement (
                                id BIGINT IDENTITY(1,1) NOT NULL,
                                branch_id BIGINT NOT NULL,
                                product_id BIGINT NOT NULL,
                                quantity_delta INT NOT NULL,
                                reason NVARCHAR(200) NOT NULL,
                                performed_by_user_id BIGINT NOT NULL,
                                created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_stock_movement_created_at DEFAULT SYSUTCDATETIME(),

                                CONSTRAINT PK_stock_movement PRIMARY KEY (id),

                                CONSTRAINT FK_stock_movement_branch
                                    FOREIGN KEY (branch_id)
                                        REFERENCES branch(id),

                                CONSTRAINT FK_stock_movement_product
                                    FOREIGN KEY (product_id)
                                        REFERENCES product(id),

                                CONSTRAINT FK_stock_movement_user
                                    FOREIGN KEY (performed_by_user_id)
                                        REFERENCES app_user(id),

                                CONSTRAINT CK_stock_movement_delta
                                    CHECK (quantity_delta <> 0)
);
GO

CREATE INDEX IX_stock_movement_branch_time
    ON stock_movement(branch_id, created_at_utc DESC);
GO

CREATE INDEX IX_stock_movement_product_time
    ON stock_movement(product_id, created_at_utc DESC);
GO

CREATE TABLE sales_order (
                             id BIGINT IDENTITY(1,1) NOT NULL,
                             order_code VARCHAR(40) NOT NULL,
                             branch_id BIGINT NOT NULL,
                             member_id BIGINT NULL,
                             status VARCHAR(30) NOT NULL,
                             subtotal DECIMAL(18,2) NOT NULL,
                             total DECIMAL(18,2) NOT NULL,
                             created_by_user_id BIGINT NOT NULL,
                             created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_sales_order_created_at DEFAULT SYSUTCDATETIME(),
                             paid_at_utc DATETIME2(0) NULL,

                             CONSTRAINT PK_sales_order PRIMARY KEY (id),

                             CONSTRAINT UQ_sales_order_code
                                 UNIQUE (order_code),

                             CONSTRAINT FK_sales_order_branch
                                 FOREIGN KEY (branch_id)
                                     REFERENCES branch(id),

                             CONSTRAINT FK_sales_order_member
                                 FOREIGN KEY (member_id)
                                     REFERENCES member(id),

                             CONSTRAINT FK_sales_order_created_by
                                 FOREIGN KEY (created_by_user_id)
                                     REFERENCES app_user(id),

                             CONSTRAINT CK_sales_order_status
                                 CHECK (status IN (
                                                   'PENDING_PAYMENT',
                                                   'PAID',
                                                   'CANCELLED'
                                     )),

                             CONSTRAINT CK_sales_order_subtotal
                                 CHECK (subtotal >= 0),

                             CONSTRAINT CK_sales_order_total
                                 CHECK (total >= 0),

                             CONSTRAINT CK_sales_order_paid_time
                                 CHECK (
                                     (status = 'PAID' AND paid_at_utc IS NOT NULL)
                                         OR
                                     (status <> 'PAID')
                                     )
);
GO

CREATE INDEX IX_sales_order_branch_time
    ON sales_order(branch_id, created_at_utc DESC);
GO

CREATE INDEX IX_sales_order_member_time
    ON sales_order(member_id, created_at_utc DESC);
GO

CREATE INDEX IX_sales_order_status
    ON sales_order(status);
GO

CREATE TABLE sales_order_item (
                                  id BIGINT IDENTITY(1,1) NOT NULL,
                                  order_id BIGINT NOT NULL,
                                  item_type VARCHAR(20) NOT NULL,
                                  plan_id BIGINT NULL,
                                  product_id BIGINT NULL,
                                  name_snapshot NVARCHAR(140) NOT NULL,
                                  unit_price DECIMAL(18,2) NOT NULL,
                                  quantity INT NOT NULL,
                                  line_total DECIMAL(18,2) NOT NULL,

                                  CONSTRAINT PK_sales_order_item PRIMARY KEY (id),

                                  CONSTRAINT FK_sales_order_item_order
                                      FOREIGN KEY (order_id)
                                          REFERENCES sales_order(id),

                                  CONSTRAINT FK_sales_order_item_plan
                                      FOREIGN KEY (plan_id)
                                          REFERENCES membership_plan(id),

                                  CONSTRAINT FK_sales_order_item_product
                                      FOREIGN KEY (product_id)
                                          REFERENCES product(id),

                                  CONSTRAINT CK_sales_order_item_type
                                      CHECK (item_type IN (
                                                           'PLAN',
                                                           'PRODUCT'
                                          )),

                                  CONSTRAINT CK_sales_order_item_reference
                                      CHECK (
                                          (
                                              item_type = 'PLAN'
                                                  AND plan_id IS NOT NULL
                                                  AND product_id IS NULL
                                              )
                                              OR
                                          (
                                              item_type = 'PRODUCT'
                                                  AND product_id IS NOT NULL
                                                  AND plan_id IS NULL
                                              )
                                          ),

                                  CONSTRAINT CK_sales_order_item_unit_price
                                      CHECK (unit_price >= 0),

                                  CONSTRAINT CK_sales_order_item_quantity
                                      CHECK (quantity >= 1),

                                  CONSTRAINT CK_sales_order_item_line_total
                                      CHECK (line_total >= 0)
);
GO

CREATE INDEX IX_sales_order_item_order
    ON sales_order_item(order_id);
GO

CREATE TABLE payment (
                         id BIGINT IDENTITY(1,1) NOT NULL,
                         payment_code VARCHAR(40) NOT NULL,
                         order_id BIGINT NOT NULL,
                         method VARCHAR(20) NOT NULL,
                         provider VARCHAR(30) NOT NULL,
                         status VARCHAR(20) NOT NULL,
                         amount DECIMAL(18,2) NOT NULL,
                         idempotency_key VARCHAR(100) NOT NULL,
                         provider_reference VARCHAR(100) NULL,
                         created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_payment_created_at DEFAULT SYSUTCDATETIME(),
                         paid_at_utc DATETIME2(0) NULL,

                         CONSTRAINT PK_payment PRIMARY KEY (id),

                         CONSTRAINT UQ_payment_code
                             UNIQUE (payment_code),

                         CONSTRAINT UQ_payment_idempotency_key
                             UNIQUE (idempotency_key),

                         CONSTRAINT FK_payment_order
                             FOREIGN KEY (order_id)
                                 REFERENCES sales_order(id),

                         CONSTRAINT CK_payment_method
                             CHECK (method IN ('MOMO')),

                         CONSTRAINT CK_payment_provider
                             CHECK (provider IN ('MOMO_SIMULATOR')),

                         CONSTRAINT CK_payment_status
                             CHECK (status IN (
                                               'PENDING',
                                               'SUCCEEDED',
                                               'FAILED',
                                               'CANCELLED'
                                 )),

                         CONSTRAINT CK_payment_amount
                             CHECK (amount > 0),

                         CONSTRAINT CK_payment_paid_time
                             CHECK (
                                 (status = 'SUCCEEDED' AND paid_at_utc IS NOT NULL)
                                     OR
                                 (status <> 'SUCCEEDED')
                                 )
);
GO

CREATE INDEX IX_payment_order
    ON payment(order_id);
GO

CREATE INDEX IX_payment_status_time
    ON payment(status, created_at_utc DESC);
GO

CREATE TABLE membership (
                            id BIGINT IDENTITY(1,1) NOT NULL,
                            member_id BIGINT NOT NULL,
                            plan_id BIGINT NOT NULL,
                            order_id BIGINT NOT NULL,
                            branch_id BIGINT NOT NULL,
                            status VARCHAR(30) NOT NULL,
                            start_date DATE NOT NULL,
                            end_date DATE NOT NULL,
                            activated_at_utc DATETIME2(0) NOT NULL,
                            ended_at_utc DATETIME2(0) NULL,
                            replaced_by_membership_id BIGINT NULL,
                            created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_membership_created_at DEFAULT SYSUTCDATETIME(),

                            CONSTRAINT PK_membership PRIMARY KEY (id),

                            CONSTRAINT FK_membership_member
                                FOREIGN KEY (member_id)
                                    REFERENCES member(id),

                            CONSTRAINT FK_membership_plan
                                FOREIGN KEY (plan_id)
                                    REFERENCES membership_plan(id),

                            CONSTRAINT FK_membership_order
                                FOREIGN KEY (order_id)
                                    REFERENCES sales_order(id),

                            CONSTRAINT FK_membership_branch
                                FOREIGN KEY (branch_id)
                                    REFERENCES branch(id),

                            CONSTRAINT FK_membership_replaced_by
                                FOREIGN KEY (replaced_by_membership_id)
                                    REFERENCES membership(id),

                            CONSTRAINT CK_membership_status
                                CHECK (status IN (
                                                  'ACTIVE',
                                                  'EXPIRED',
                                                  'REPLACED',
                                                  'CANCELLED'
                                    )),

                            CONSTRAINT CK_membership_date
                                CHECK (start_date <= end_date),

                            CONSTRAINT CK_membership_ended
                                CHECK (
                                    (
                                        status = 'ACTIVE'
                                            AND ended_at_utc IS NULL
                                        )
                                        OR
                                    (
                                        status <> 'ACTIVE'
                                        )
                                    )
);
GO

-- Một hội viên được phép có nhiều membership ACTIVE cùng lúc.
-- Không tạo unique index theo member_id ở đây.

CREATE INDEX IX_membership_member_history
    ON membership(member_id, created_at_utc DESC);
GO

CREATE INDEX IX_membership_branch_status
    ON membership(branch_id, status);
GO

CREATE INDEX IX_membership_end_date
    ON membership(end_date, status);
GO

CREATE TABLE membership_service_access (
                                           membership_id BIGINT NOT NULL,
                                           service_code VARCHAR(20) NOT NULL,

                                           CONSTRAINT PK_membership_service_access
                                               PRIMARY KEY (membership_id, service_code),

                                           CONSTRAINT FK_membership_service_access_membership
                                               FOREIGN KEY (membership_id)
                                                   REFERENCES membership(id),

                                           CONSTRAINT CK_membership_service_access_code
                                               CHECK (service_code IN (
                                                                       'GYM',
                                                                       'BOXING',
                                                                       'PICKLEBALL'
                                                   ))
);
GO

CREATE TABLE booking (
                         id BIGINT IDENTITY(1,1) NOT NULL,
                         booking_code VARCHAR(40) NOT NULL,
                         member_id BIGINT NOT NULL,
                         membership_id BIGINT NOT NULL,
                         branch_id BIGINT NOT NULL,
                         service_code VARCHAR(20) NOT NULL,
                         facility_id BIGINT NOT NULL,
                         status VARCHAR(20) NOT NULL,
                         starts_at_utc DATETIME2(0) NOT NULL,
                         ends_at_utc DATETIME2(0) NOT NULL,
                         cancelled_at_utc DATETIME2(0) NULL,
                         cancellation_reason NVARCHAR(200) NULL,
                         created_by_user_id BIGINT NOT NULL,
                         created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_booking_created_at DEFAULT SYSUTCDATETIME(),

                         CONSTRAINT PK_booking PRIMARY KEY (id),

                         CONSTRAINT UQ_booking_code
                             UNIQUE (booking_code),

                         CONSTRAINT FK_booking_member
                             FOREIGN KEY (member_id)
                                 REFERENCES member(id),

                         CONSTRAINT FK_booking_membership
                             FOREIGN KEY (membership_id)
                                 REFERENCES membership(id),

                         CONSTRAINT FK_booking_branch
                             FOREIGN KEY (branch_id)
                                 REFERENCES branch(id),

                         CONSTRAINT FK_booking_facility
                             FOREIGN KEY (facility_id)
                                 REFERENCES facility(id),

                         CONSTRAINT FK_booking_created_by
                             FOREIGN KEY (created_by_user_id)
                                 REFERENCES app_user(id),

                         CONSTRAINT CK_booking_service
                             CHECK (service_code IN (
                                                     'GYM',
                                                     'BOXING',
                                                     'PICKLEBALL'
                                 )),

                         CONSTRAINT CK_booking_status
                             CHECK (status IN (
                                               'CONFIRMED',
                                               'COMPLETED',
                                               'CANCELLED'
                                 )),

                         CONSTRAINT CK_booking_time
                             CHECK (starts_at_utc < ends_at_utc),

                         CONSTRAINT CK_booking_cancellation
                             CHECK (
                                 (
                                     status = 'CANCELLED'
                                         AND cancelled_at_utc IS NOT NULL
                                         AND cancellation_reason IS NOT NULL
                                     )
                                     OR
                                 (
                                     status <> 'CANCELLED'
                                     )
                                 )
);
GO

CREATE INDEX IX_booking_member_time
    ON booking(member_id, starts_at_utc, ends_at_utc);
GO

CREATE INDEX IX_booking_branch_time
    ON booking(branch_id, starts_at_utc, ends_at_utc);
GO

CREATE INDEX IX_booking_facility_time
    ON booking(facility_id, starts_at_utc, ends_at_utc);
GO

CREATE INDEX IX_booking_status_start
    ON booking(status, starts_at_utc);
GO

CREATE TABLE check_in (
                          id BIGINT IDENTITY(1,1) NOT NULL,
                          member_id BIGINT NULL,
                          membership_id BIGINT NULL,
                          branch_id BIGINT NOT NULL,
                          service_code VARCHAR(20) NOT NULL,
                          method VARCHAR(20) NOT NULL,
                          result VARCHAR(20) NOT NULL,
                          reason VARCHAR(60) NULL,
                          performed_by_user_id BIGINT NOT NULL,
                          created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_check_in_created_at DEFAULT SYSUTCDATETIME(),

                          CONSTRAINT PK_check_in PRIMARY KEY (id),

                          CONSTRAINT FK_check_in_member
                              FOREIGN KEY (member_id)
                                  REFERENCES member(id),

                          CONSTRAINT FK_check_in_membership
                              FOREIGN KEY (membership_id)
                                  REFERENCES membership(id),

                          CONSTRAINT FK_check_in_branch
                              FOREIGN KEY (branch_id)
                                  REFERENCES branch(id),

                          CONSTRAINT FK_check_in_performed_by
                              FOREIGN KEY (performed_by_user_id)
                                  REFERENCES app_user(id),

                          CONSTRAINT CK_check_in_service
                              CHECK (service_code IN (
                                                      'GYM',
                                                      'BOXING',
                                                      'PICKLEBALL'
                                  )),

                          CONSTRAINT CK_check_in_method
                              CHECK (method IN (
                                                'MANUAL',
                                                'QR'
                                  )),

                          CONSTRAINT CK_check_in_result
                              CHECK (result IN (
                                                'ACCEPTED',
                                                'REJECTED'
                                  )),

                          CONSTRAINT CK_check_in_accept_data
                              CHECK (
                                  (
                                      result = 'ACCEPTED'
                                          AND member_id IS NOT NULL
                                          AND membership_id IS NOT NULL
                                          AND reason IS NULL
                                      )
                                      OR
                                  (
                                      result = 'REJECTED'
                                      )
                                  )
);
GO

CREATE INDEX IX_check_in_member_time
    ON check_in(member_id, created_at_utc DESC);
GO

CREATE INDEX IX_check_in_branch_time
    ON check_in(branch_id, created_at_utc DESC);
GO

CREATE INDEX IX_check_in_result_time
    ON check_in(result, created_at_utc DESC);
GO

CREATE TABLE qr_token_use (
                              id BIGINT IDENTITY(1,1) NOT NULL,
                              token_jti VARCHAR(80) NOT NULL,
                              member_id BIGINT NOT NULL,
                              used_at_utc DATETIME2(0) NOT NULL,

                              CONSTRAINT PK_qr_token_use PRIMARY KEY (id),

                              CONSTRAINT UQ_qr_token_use_jti
                                  UNIQUE (token_jti),

                              CONSTRAINT FK_qr_token_use_member
                                  FOREIGN KEY (member_id)
                                      REFERENCES member(id)
);
GO

CREATE INDEX IX_qr_token_use_member_time
    ON qr_token_use(member_id, used_at_utc DESC);
GO

CREATE TABLE audit_event (
                             id BIGINT IDENTITY(1,1) NOT NULL,
                             actor_user_id BIGINT NULL,
                             action VARCHAR(60) NOT NULL,
                             entity_type VARCHAR(60) NOT NULL,
                             entity_id BIGINT NULL,
                             branch_id BIGINT NULL,
                             details_json NVARCHAR(MAX) NULL,
                             created_at_utc DATETIME2(0) NOT NULL CONSTRAINT DF_audit_event_created_at DEFAULT SYSUTCDATETIME(),

                             CONSTRAINT PK_audit_event PRIMARY KEY (id),

                             CONSTRAINT FK_audit_event_actor
                                 FOREIGN KEY (actor_user_id)
                                     REFERENCES app_user(id),

                             CONSTRAINT FK_audit_event_branch
                                 FOREIGN KEY (branch_id)
                                     REFERENCES branch(id),

                             CONSTRAINT CK_audit_event_json
                                 CHECK (
                                     details_json IS NULL
                                         OR ISJSON(details_json) = 1
                                     )
);
GO

CREATE INDEX IX_audit_event_time
    ON audit_event(created_at_utc DESC);
GO

CREATE INDEX IX_audit_event_actor_time
    ON audit_event(actor_user_id, created_at_utc DESC);
GO

CREATE INDEX IX_audit_event_branch_time
    ON audit_event(branch_id, created_at_utc DESC);
GO

INSERT INTO branch (
    code,
    name,
    address,
    phone,
    status,
    timezone
)
VALUES
(
    'Q1',
    N'GYMFIT Quận 1',
    N'12 Nguyễn Huệ, Phường Sài Gòn, TP.HCM',
    '02873001001',
    'ACTIVE',
    'Asia/Ho_Chi_Minh'
),
(
    'Q7',
    N'GYMFIT Quận 7',
    N'88 Nguyễn Thị Thập, Phường Tân Hưng, TP.HCM',
    '02873001007',
    'ACTIVE',
    'Asia/Ho_Chi_Minh'
),
(
    'TD',
    N'GYMFIT Thủ Đức',
    N'28 Võ Văn Ngân, Phường Thủ Đức, TP.HCM',
    '02873001003',
    'ACTIVE',
    'Asia/Ho_Chi_Minh'
),
(
    'BT',
    N'GYMFIT Bình Thạnh',
    N'220 Xô Viết Nghệ Tĩnh, Phường Bình Thạnh, TP.HCM',
    '02873001004',
    'ACTIVE',
    'Asia/Ho_Chi_Minh'
);
GO

INSERT INTO branch_service_config (
    branch_id,
    service_code,
    booking_duration_minutes,
    capacity,
    booking_enabled
)
VALUES
(1, 'GYM', 60, 80, 1),
(1, 'BOXING', 60, 20, 1),
(1, 'PICKLEBALL', 60, 4, 1),

(2, 'GYM', 60, 70, 1),
(2, 'PICKLEBALL', 60, 4, 1),

(3, 'GYM', 60, 60, 1),
(3, 'BOXING', 60, 16, 1),

(4, 'GYM', 60, 55, 1);
GO

INSERT INTO branch_operating_hour (
    branch_id,
    day_of_week,
    open_time,
    close_time
)
SELECT
    b.id,
    d.day_of_week,
    d.open_time,
    d.close_time
FROM branch b
         CROSS JOIN (
    SELECT
        1 AS day_of_week,
        CAST('06:00' AS TIME(0)) AS open_time,
        CAST('22:00' AS TIME(0)) AS close_time

    UNION ALL SELECT 2, '06:00', '22:00'
    UNION ALL SELECT 3, '06:00', '22:00'
    UNION ALL SELECT 4, '06:00', '22:00'
    UNION ALL SELECT 5, '06:00', '22:00'
    UNION ALL SELECT 6, '06:00', '22:00'
    UNION ALL SELECT 7, '08:00', '20:00'
) d;
GO

INSERT INTO facility (
    branch_id,
    service_code,
    facility_type,
    name,
    capacity,
    status
)
VALUES
(1, 'GYM', 'GYM_AREA', N'Khu Gym A', 80, 'ACTIVE'),
(1, 'BOXING', 'BOXING_ROOM', N'Phòng Boxing 1', 20, 'ACTIVE'),
(1, 'PICKLEBALL', 'PICKLEBALL_COURT', N'Sân Pickleball 1', 4, 'ACTIVE'),
(1, 'PICKLEBALL', 'PICKLEBALL_COURT', N'Sân Pickleball 2', 4, 'ACTIVE'),

(2, 'GYM', 'GYM_AREA', N'Khu Gym A', 70, 'ACTIVE'),
(2, 'PICKLEBALL', 'PICKLEBALL_COURT', N'Sân Pickleball 1', 4, 'ACTIVE'),
(2, 'PICKLEBALL', 'PICKLEBALL_COURT', N'Sân Pickleball 2', 4, 'ACTIVE'),

(3, 'GYM', 'GYM_AREA', N'Khu Gym A', 60, 'ACTIVE'),
(3, 'BOXING', 'BOXING_ROOM', N'Phòng Boxing 1', 16, 'ACTIVE'),

(4, 'GYM', 'GYM_AREA', N'Khu Gym A', 55, 'ACTIVE');
GO

INSERT INTO member (
    member_code,
    full_name,
    phone,
    email,
    home_branch_id,
    date_of_birth,
    status
)
VALUES
(
    'GF000001',
    N'Nguyễn Văn Anh',
    '0901000001',
    'member1@gymfit.local',
    1,
    '2000-04-18',
    'ACTIVE'
),
(
    'GF000002',
    N'Trần Thị Bích',
    '0901000002',
    'member2@gymfit.local',
    1,
    '1999-09-12',
    'ACTIVE'
),
(
    'GF000003',
    N'Lê Minh Cường',
    '0901000003',
    'member3@gymfit.local',
    2,
    '2001-01-20',
    'ACTIVE'
),
(
    'GF000004',
    N'Phạm Thu Trang',
    '0901000004',
    'member4@gymfit.local',
    3,
    '2000-07-07',
    'ACTIVE'
),
(
    'GF000005',
    N'Hoàng Văn Lâm',
    '0901000005',
    'member5@gymfit.local',
    4,
    '1998-03-25',
    'ACTIVE'
),
(
    'GF000006',
    N'Võ Trí Hoàng',
    '0901000006',
    'member6@gymfit.local',
    1,
    '2002-06-14',
    'ACTIVE'
),
(
    'GF000007',
    N'Nguyễn Ngọc Hà',
    '0901000007',
    'member7@gymfit.local',
    2,
    '2001-11-05',
    'ACTIVE'
),
(
    'GF000008',
    N'Đặng Quốc Bảo',
    '0901000008',
    'member8@gymfit.local',
    3,
    '1997-08-22',
    'ACTIVE'
),
(
    'GF000009',
    N'Bùi Thanh Mai',
    '0901000009',
    'member9@gymfit.local',
    1,
    '2000-02-17',
    'ACTIVE'
),
(
    'GF000010',
    N'Ngô Minh Khoa',
    '0901000010',
    'member10@gymfit.local',
    4,
    '1999-12-01',
    'INACTIVE'
);
GO

INSERT INTO app_user (
    full_name,
    email,
    password_hash,
    role_code,
    status,
    branch_id,
    member_id
)
VALUES
(
    N'Quản trị GYMFIT',
    'admin@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'ADMIN',
    'ACTIVE',
    NULL,
    NULL
),
(
    N'Quản lý GYMFIT Quận 1',
    'manager.q1@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'BRANCH_MANAGER',
    'ACTIVE',
    1,
    NULL
),
(
    N'Quản lý GYMFIT Quận 7',
    'manager.q7@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'BRANCH_MANAGER',
    'ACTIVE',
    2,
    NULL
),
(
    N'Quản lý GYMFIT Thủ Đức',
    'manager.td@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'BRANCH_MANAGER',
    'ACTIVE',
    3,
    NULL
),
(
    N'Quản lý GYMFIT Bình Thạnh',
    'manager.bt@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'BRANCH_MANAGER',
    'ACTIVE',
    4,
    NULL
),
(
    N'Nguyễn Văn Anh',
    'member1@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'MEMBER',
    'ACTIVE',
    NULL,
    1
),
(
    N'Trần Thị Bích',
    'member2@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'MEMBER',
    'ACTIVE',
    NULL,
    2
),
(
    N'Lê Minh Cường',
    'member3@gymfit.local',
    '$2a$12$nMYWtLmrCKlYy0FsPdqs0OIcNI60/qmDQS7Vo77BcVuU5Xu9eQ3KO',
    'MEMBER',
    'ACTIVE',
    NULL,
    3
);
GO

INSERT INTO membership_plan (
    branch_id,
    plan_code,
    name,
    tier,
    duration_days,
    price,
    description,
    status
)
VALUES
(
    1,
    'Q1_GYM_1M',
    N'Gym 1 tháng',
    'BASIC',
    30,
    350000,
    N'Tập Gym trong 30 ngày tại GYMFIT Quận 1',
    'ACTIVE'
),
(
    1,
    'Q1_GYM_3M',
    N'Gym 3 tháng',
    'STANDARD',
    90,
    900000,
    N'Tập Gym trong 90 ngày tại GYMFIT Quận 1',
    'ACTIVE'
),
(
    1,
    'Q1_PREMIUM_1M',
    N'Premium 1 tháng',
    'PREMIUM',
    30,
    1500000,
    N'Sử dụng Gym, Boxing và Pickleball trong 30 ngày',
    'ACTIVE'
),
(
    1,
    'Q1_PREMIUM_3M',
    N'Premium 3 tháng',
    'PREMIUM',
    90,
    2700000,
    N'Sử dụng Gym, Boxing và Pickleball trong 90 ngày',
    'ACTIVE'
),
(
    1,
    'Q1_BOXING_1M',
    N'Boxing 1 tháng',
    'BASIC',
    30,
    500000,
    N'Tập Boxing trong 30 ngày',
    'ACTIVE'
),
(
    1,
    'Q1_BOXING_VIP',
    N'Boxing VIP',
    'PREMIUM',
    30,
    1200000,
    N'Gói Boxing nâng cao trong 30 ngày',
    'ACTIVE'
),
(
    1,
    'Q1_PICKLEBALL_1M',
    N'Pickleball 1 tháng',
    'STANDARD',
    30,
    1800000,
    N'Đặt sân Pickleball trong 30 ngày',
    'ACTIVE'
),

(
    2,
    'Q7_GYM_1M',
    N'Gym 1 tháng',
    'BASIC',
    30,
    350000,
    N'Tập Gym trong 30 ngày tại GYMFIT Quận 7',
    'ACTIVE'
),
(
    2,
    'Q7_GYM_3M',
    N'Gym 3 tháng',
    'STANDARD',
    90,
    900000,
    N'Tập Gym trong 90 ngày tại GYMFIT Quận 7',
    'ACTIVE'
),
(
    2,
    'Q7_PICKLEBALL_1M',
    N'Pickleball 1 tháng',
    'STANDARD',
    30,
    1700000,
    N'Đặt sân Pickleball tại GYMFIT Quận 7',
    'ACTIVE'
),
(
    2,
    'Q7_COMBO_1M',
    N'Gym + Pickleball',
    'PREMIUM',
    30,
    1950000,
    N'Sử dụng Gym và Pickleball trong 30 ngày',
    'ACTIVE'
),

(
    3,
    'TD_GYM_1M',
    N'Gym 1 tháng',
    'BASIC',
    30,
    330000,
    N'Tập Gym trong 30 ngày tại GYMFIT Thủ Đức',
    'ACTIVE'
),
(
    3,
    'TD_BOXING_1M',
    N'Boxing 1 tháng',
    'STANDARD',
    30,
    480000,
    N'Tập Boxing trong 30 ngày tại GYMFIT Thủ Đức',
    'ACTIVE'
),
(
    3,
    'TD_COMBO_1M',
    N'Gym + Boxing',
    'PREMIUM',
    30,
    750000,
    N'Sử dụng Gym và Boxing trong 30 ngày',
    'ACTIVE'
),

(
    4,
    'BT_GYM_1M',
    N'Gym 1 tháng',
    'BASIC',
    30,
    320000,
    N'Tập Gym trong 30 ngày tại GYMFIT Bình Thạnh',
    'ACTIVE'
),
(
    4,
    'BT_GYM_3M',
    N'Gym 3 tháng',
    'STANDARD',
    90,
    850000,
    N'Tập Gym trong 90 ngày tại GYMFIT Bình Thạnh',
    'ACTIVE'
);
GO

INSERT INTO plan_service (
    plan_id,
    service_code
)
SELECT id, 'GYM'
FROM membership_plan
WHERE plan_code IN (
                    'Q1_GYM_1M',
                    'Q1_GYM_3M',
                    'Q7_GYM_1M',
                    'Q7_GYM_3M',
                    'TD_GYM_1M',
                    'BT_GYM_1M',
                    'BT_GYM_3M'
    );
GO

INSERT INTO plan_service (
    plan_id,
    service_code
)
SELECT id, 'BOXING'
FROM membership_plan
WHERE plan_code IN (
                    'Q1_BOXING_1M',
                    'Q1_BOXING_VIP',
                    'TD_BOXING_1M'
    );
GO

INSERT INTO plan_service (
    plan_id,
    service_code
)
SELECT id, 'PICKLEBALL'
FROM membership_plan
WHERE plan_code IN (
                    'Q1_PICKLEBALL_1M',
                    'Q7_PICKLEBALL_1M'
    );
GO

INSERT INTO plan_service (
    plan_id,
    service_code
)
SELECT id, 'GYM'
FROM membership_plan
WHERE plan_code IN (
                    'Q1_PREMIUM_1M',
                    'Q1_PREMIUM_3M',
                    'Q7_COMBO_1M',
                    'TD_COMBO_1M'
    );
GO

INSERT INTO plan_service (
    plan_id,
    service_code
)
SELECT id, 'BOXING'
FROM membership_plan
WHERE plan_code IN (
                    'Q1_PREMIUM_1M',
                    'Q1_PREMIUM_3M',
                    'TD_COMBO_1M'
    );
GO

INSERT INTO plan_service (
    plan_id,
    service_code
)
SELECT id, 'PICKLEBALL'
FROM membership_plan
WHERE plan_code IN (
                    'Q1_PREMIUM_1M',
                    'Q1_PREMIUM_3M',
                    'Q7_COMBO_1M'
    );
GO

INSERT INTO product (
    sku,
    name,
    category,
    price,
    status
)
VALUES
(
    'WATER500',
    N'Nước suối 500ml',
    N'Đồ uống',
    10000,
    'ACTIVE'
),
(
    'ELECTROLYTE',
    N'Nước điện giải',
    N'Đồ uống',
    30000,
    'ACTIVE'
),
(
    'PROTEINBAR',
    N'Protein Bar',
    N'Dinh dưỡng',
    35000,
    'ACTIVE'
),
(
    'SHAKER',
    N'Bình lắc GYMFIT',
    N'Phụ kiện',
    80000,
    'ACTIVE'
),
(
    'TOWEL',
    N'Khăn tập GYMFIT',
    N'Phụ kiện',
    50000,
    'ACTIVE'
),
(
    'BOXWRAP',
    N'Băng quấn tay Boxing',
    N'Boxing',
    65000,
    'ACTIVE'
),
(
    'BOXGLOVE',
    N'Găng Boxing',
    N'Boxing',
    350000,
    'ACTIVE'
),
(
    'PICKBALL3',
    N'Bóng Pickleball set 3',
    N'Pickleball',
    90000,
    'ACTIVE'
),
(
    'PICKPADDLE',
    N'Vợt Pickleball',
    N'Pickleball',
    650000,
    'ACTIVE'
);
GO

INSERT INTO stock_level (
    branch_id,
    product_id,
    quantity
)
SELECT
    b.id,
    p.id,
    CASE p.sku
        WHEN 'WATER500' THEN 150
        WHEN 'ELECTROLYTE' THEN 80
        WHEN 'PROTEINBAR' THEN 50
        WHEN 'SHAKER' THEN 30
        WHEN 'TOWEL' THEN 40
        WHEN 'BOXWRAP' THEN 25
        WHEN 'BOXGLOVE' THEN 15
        WHEN 'PICKBALL3' THEN 20
        WHEN 'PICKPADDLE' THEN 10
        ELSE 10
        END
FROM branch b
         CROSS JOIN product p;
GO

DECLARE @premiumPlanId BIGINT;

SELECT @premiumPlanId = id
FROM membership_plan
WHERE plan_code = 'Q1_PREMIUM_1M';

INSERT INTO sales_order (
    order_code,
    branch_id,
    member_id,
    status,
    subtotal,
    total,
    created_by_user_id,
    created_at_utc,
    paid_at_utc
)
VALUES (
           'ORD_SEED_000001',
           1,
           1,
           'PAID',
           1500000,
           1500000,
           1,
           DATEADD(DAY, -5, SYSUTCDATETIME()),
           DATEADD(DAY, -5, SYSUTCDATETIME())
       );

DECLARE @seedOrderId BIGINT = SCOPE_IDENTITY();

INSERT INTO sales_order_item (
    order_id,
    item_type,
    plan_id,
    product_id,
    name_snapshot,
    unit_price,
    quantity,
    line_total
)
VALUES (
           @seedOrderId,
           'PLAN',
           @premiumPlanId,
           NULL,
           N'Premium 1 tháng',
           1500000,
           1,
           1500000
       );

INSERT INTO payment (
    payment_code,
    order_id,
    method,
    provider,
    status,
    amount,
    idempotency_key,
    provider_reference,
    created_at_utc,
    paid_at_utc
)
VALUES (
           'PAY_SEED_000001',
           @seedOrderId,
           'MOMO',
           'MOMO_SIMULATOR',
           'SUCCEEDED',
           1500000,
           'seed-member1-payment',
           'MOMO_SEED_000001',
           DATEADD(DAY, -5, SYSUTCDATETIME()),
           DATEADD(DAY, -5, SYSUTCDATETIME())
       );

INSERT INTO membership (
    member_id,
    plan_id,
    order_id,
    branch_id,
    status,
    start_date,
    end_date,
    activated_at_utc,
    ended_at_utc,
    replaced_by_membership_id,
    created_at_utc
)
VALUES (
           1,
           @premiumPlanId,
           @seedOrderId,
           1,
           'ACTIVE',
           CAST(DATEADD(DAY, -5, GETDATE()) AS DATE),
           CAST(DATEADD(DAY, 24, GETDATE()) AS DATE),
           DATEADD(DAY, -5, SYSUTCDATETIME()),
           NULL,
           NULL,
           DATEADD(DAY, -5, SYSUTCDATETIME())
       );

DECLARE @seedMembershipId BIGINT = SCOPE_IDENTITY();

INSERT INTO membership_service_access (
    membership_id,
    service_code
)
VALUES
    (@seedMembershipId, 'GYM'),
    (@seedMembershipId, 'BOXING'),
    (@seedMembershipId, 'PICKLEBALL');
GO

DECLARE @member2Plan BIGINT;

SELECT @member2Plan = id
FROM membership_plan
WHERE plan_code = 'Q1_GYM_1M';

INSERT INTO sales_order (
    order_code,
    branch_id,
    member_id,
    status,
    subtotal,
    total,
    created_by_user_id,
    created_at_utc,
    paid_at_utc
)
VALUES (
           'ORD_SEED_000002',
           1,
           2,
           'PAID',
           350000,
           350000,
           2,
           DATEADD(DAY, -3, SYSUTCDATETIME()),
           DATEADD(DAY, -3, SYSUTCDATETIME())
       );

DECLARE @seedOrder2 BIGINT = SCOPE_IDENTITY();

INSERT INTO sales_order_item (
    order_id,
    item_type,
    plan_id,
    product_id,
    name_snapshot,
    unit_price,
    quantity,
    line_total
)
VALUES (
           @seedOrder2,
           'PLAN',
           @member2Plan,
           NULL,
           N'Gym 1 tháng',
           350000,
           1,
           350000
       );

INSERT INTO payment (
    payment_code,
    order_id,
    method,
    provider,
    status,
    amount,
    idempotency_key,
    provider_reference,
    created_at_utc,
    paid_at_utc
)
VALUES (
           'PAY_SEED_000002',
           @seedOrder2,
           'MOMO',
           'MOMO_SIMULATOR',
           'SUCCEEDED',
           350000,
           'seed-member2-payment',
           'MOMO_SEED_000002',
           DATEADD(DAY, -3, SYSUTCDATETIME()),
           DATEADD(DAY, -3, SYSUTCDATETIME())
       );

INSERT INTO membership (
    member_id,
    plan_id,
    order_id,
    branch_id,
    status,
    start_date,
    end_date,
    activated_at_utc,
    ended_at_utc,
    replaced_by_membership_id,
    created_at_utc
)
VALUES (
           2,
           @member2Plan,
           @seedOrder2,
           1,
           'ACTIVE',
           CAST(DATEADD(DAY, -3, GETDATE()) AS DATE),
           CAST(DATEADD(DAY, 26, GETDATE()) AS DATE),
           DATEADD(DAY, -3, SYSUTCDATETIME()),
           NULL,
           NULL,
           DATEADD(DAY, -3, SYSUTCDATETIME())
       );

DECLARE @seedMembership2 BIGINT = SCOPE_IDENTITY();

INSERT INTO membership_service_access (
    membership_id,
    service_code
)
VALUES (
           @seedMembership2,
           'GYM'
       );
GO

DECLARE @member3Plan BIGINT;

SELECT @member3Plan = id
FROM membership_plan
WHERE plan_code = 'Q7_COMBO_1M';

INSERT INTO sales_order (
    order_code,
    branch_id,
    member_id,
    status,
    subtotal,
    total,
    created_by_user_id,
    created_at_utc,
    paid_at_utc
)
VALUES (
           'ORD_SEED_000003',
           2,
           3,
           'PAID',
           1950000,
           1950000,
           3,
           DATEADD(DAY, -7, SYSUTCDATETIME()),
           DATEADD(DAY, -7, SYSUTCDATETIME())
       );

DECLARE @seedOrder3 BIGINT = SCOPE_IDENTITY();

INSERT INTO sales_order_item (
    order_id,
    item_type,
    plan_id,
    product_id,
    name_snapshot,
    unit_price,
    quantity,
    line_total
)
VALUES (
           @seedOrder3,
           'PLAN',
           @member3Plan,
           NULL,
           N'Gym + Pickleball',
           1950000,
           1,
           1950000
       );

INSERT INTO payment (
    payment_code,
    order_id,
    method,
    provider,
    status,
    amount,
    idempotency_key,
    provider_reference,
    created_at_utc,
    paid_at_utc
)
VALUES (
           'PAY_SEED_000003',
           @seedOrder3,
           'MOMO',
           'MOMO_SIMULATOR',
           'SUCCEEDED',
           1950000,
           'seed-member3-payment',
           'MOMO_SEED_000003',
           DATEADD(DAY, -7, SYSUTCDATETIME()),
           DATEADD(DAY, -7, SYSUTCDATETIME())
       );

INSERT INTO membership (
    member_id,
    plan_id,
    order_id,
    branch_id,
    status,
    start_date,
    end_date,
    activated_at_utc,
    ended_at_utc,
    replaced_by_membership_id,
    created_at_utc
)
VALUES (
           3,
           @member3Plan,
           @seedOrder3,
           2,
           'ACTIVE',
           CAST(DATEADD(DAY, -7, GETDATE()) AS DATE),
           CAST(DATEADD(DAY, 22, GETDATE()) AS DATE),
           DATEADD(DAY, -7, SYSUTCDATETIME()),
           NULL,
           NULL,
           DATEADD(DAY, -7, SYSUTCDATETIME())
       );

DECLARE @seedMembership3 BIGINT = SCOPE_IDENTITY();

INSERT INTO membership_service_access (
    membership_id,
    service_code
)
VALUES
    (@seedMembership3, 'GYM'),
    (@seedMembership3, 'PICKLEBALL');
GO

DECLARE @member1Membership BIGINT;
DECLARE @member2Membership BIGINT;
DECLARE @member3Membership BIGINT;

SELECT @member1Membership = id
FROM membership
WHERE member_id = 1
  AND status = 'ACTIVE';

SELECT @member2Membership = id
FROM membership
WHERE member_id = 2
  AND status = 'ACTIVE';

SELECT @member3Membership = id
FROM membership
WHERE member_id = 3
  AND status = 'ACTIVE';

DECLARE @q1Gym BIGINT;
DECLARE @q1Boxing BIGINT;
DECLARE @q1Pickleball BIGINT;
DECLARE @q7Gym BIGINT;
DECLARE @q7Pickleball BIGINT;

SELECT TOP 1 @q1Gym = id
FROM facility
WHERE branch_id = 1
  AND service_code = 'GYM'
  AND status = 'ACTIVE';

SELECT TOP 1 @q1Boxing = id
FROM facility
WHERE branch_id = 1
  AND service_code = 'BOXING'
  AND status = 'ACTIVE';

SELECT TOP 1 @q1Pickleball = id
FROM facility
WHERE branch_id = 1
  AND service_code = 'PICKLEBALL'
  AND status = 'ACTIVE';

SELECT TOP 1 @q7Gym = id
FROM facility
WHERE branch_id = 2
  AND service_code = 'GYM'
  AND status = 'ACTIVE';

SELECT TOP 1 @q7Pickleball = id
FROM facility
WHERE branch_id = 2
  AND service_code = 'PICKLEBALL'
  AND status = 'ACTIVE';

INSERT INTO booking (
    booking_code,
    member_id,
    membership_id,
    branch_id,
    service_code,
    facility_id,
    status,
    starts_at_utc,
    ends_at_utc,
    cancelled_at_utc,
    cancellation_reason,
    created_by_user_id,
    created_at_utc
)
VALUES
    (
        'BOOK_SEED_000001',
        1,
        @member1Membership,
        1,
        'GYM',
        @q1Gym,
        'COMPLETED',
        DATEADD(HOUR, -26, SYSUTCDATETIME()),
        DATEADD(HOUR, -25, SYSUTCDATETIME()),
        NULL,
        NULL,
        6,
        DATEADD(HOUR, -30, SYSUTCDATETIME())
    ),
    (
        'BOOK_SEED_000002',
        1,
        @member1Membership,
        1,
        'BOXING',
        @q1Boxing,
        'CONFIRMED',
        DATEADD(DAY, 2, SYSUTCDATETIME()),
        DATEADD(HOUR, 1, DATEADD(DAY, 2, SYSUTCDATETIME())),
        NULL,
        NULL,
        6,
        SYSUTCDATETIME()
    ),
    (
        'BOOK_SEED_000003',
        2,
        @member2Membership,
        1,
        'GYM',
        @q1Gym,
        'CONFIRMED',
        DATEADD(DAY, 3, SYSUTCDATETIME()),
        DATEADD(HOUR, 1, DATEADD(DAY, 3, SYSUTCDATETIME())),
        NULL,
        NULL,
        7,
        SYSUTCDATETIME()
    ),
    (
        'BOOK_SEED_000004',
        3,
        @member3Membership,
        2,
        'PICKLEBALL',
        @q7Pickleball,
        'CONFIRMED',
        DATEADD(DAY, 4, SYSUTCDATETIME()),
        DATEADD(HOUR, 1, DATEADD(DAY, 4, SYSUTCDATETIME())),
        NULL,
        NULL,
        8,
        SYSUTCDATETIME()
    );
GO

DECLARE @membership1 BIGINT;
DECLARE @membership2 BIGINT;

SELECT @membership1 = id
FROM membership
WHERE member_id = 1
  AND status = 'ACTIVE';

SELECT @membership2 = id
FROM membership
WHERE member_id = 2
  AND status = 'ACTIVE';

INSERT INTO check_in (
    member_id,
    membership_id,
    branch_id,
    service_code,
    method,
    result,
    reason,
    performed_by_user_id,
    created_at_utc
)
VALUES
    (
        1,
        @membership1,
        1,
        'GYM',
        'QR',
        'ACCEPTED',
        NULL,
        2,
        DATEADD(HOUR, -5, SYSUTCDATETIME())
    ),
    (
        2,
        @membership2,
        1,
        'GYM',
        'MANUAL',
        'ACCEPTED',
        NULL,
        2,
        DATEADD(HOUR, -3, SYSUTCDATETIME())
    ),
    (
        10,
        NULL,
        4,
        'GYM',
        'MANUAL',
        'REJECTED',
        'MEMBER_INACTIVE',
        5,
        DATEADD(HOUR, -2, SYSUTCDATETIME())
    );
GO

INSERT INTO stock_movement (
    branch_id,
    product_id,
    quantity_delta,
    reason,
    performed_by_user_id,
    created_at_utc
)
SELECT
    1,
    id,
    20,
    N'Nhập kho ban đầu',
    2,
    DATEADD(DAY, -10, SYSUTCDATETIME())
FROM product
WHERE sku IN ('WATER500', 'PROTEINBAR', 'SHAKER');
GO

INSERT INTO audit_event (
    actor_user_id,
    action,
    entity_type,
    entity_id,
    branch_id,
    details_json,
    created_at_utc
)
VALUES
(
    1,
    'DATABASE_SEEDED',
    'SYSTEM',
    NULL,
    NULL,
    N'{"application":"GYMFIT","version":"1.0.0"}',
    SYSUTCDATETIME()
),
(
    1,
    'MEMBERSHIP_ACTIVATED',
    'MEMBERSHIP',
    1,
    1,
    N'{"source":"seed"}',
    DATEADD(DAY, -5, SYSUTCDATETIME())
),
(
    1,
    'PAYMENT_SUCCEEDED',
    'PAYMENT',
    1,
    1,
    N'{"source":"seed","provider":"MOMO_SIMULATOR"}',
    DATEADD(DAY, -5, SYSUTCDATETIME())
);
GO

SELECT
    id,
    code,
    name,
    status
FROM branch
ORDER BY id;

SELECT
    branch_id,
    service_code,
    booking_duration_minutes,
    capacity,
    booking_enabled
FROM branch_service_config
ORDER BY branch_id, service_code;

SELECT
    id,
    member_code,
    full_name,
    home_branch_id,
    status
FROM member
ORDER BY id;

SELECT
    id,
    full_name,
    email,
    role_code,
    branch_id,
    member_id,
    status
FROM app_user
ORDER BY id;

SELECT
    id,
    branch_id,
    plan_code,
    name,
    tier,
    duration_days,
    price,
    status
FROM membership_plan
ORDER BY branch_id, price;

SELECT
    m.id,
    m.member_id,
    me.full_name,
    p.name AS plan_name,
    b.name AS branch_name,
    m.status,
    m.start_date,
    m.end_date
FROM membership m
         JOIN member me ON me.id = m.member_id
         JOIN membership_plan p ON p.id = m.plan_id
         JOIN branch b ON b.id = m.branch_id
ORDER BY m.id;

SELECT
    id,
    booking_code,
    member_id,
    branch_id,
    service_code,
    facility_id,
    status,
    starts_at_utc,
    ends_at_utc
FROM booking
ORDER BY starts_at_utc;

SELECT
    id,
    member_id,
    branch_id,
    service_code,
    method,
    result,
    reason,
    created_at_utc
FROM check_in
ORDER BY created_at_utc DESC;
GO
-- ============================================================
-- CHATBOT (T7): chat_session, chat_message, chat_training_candidate
-- ============================================================

CREATE TABLE chat_session (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    state_json NVARCHAR(MAX) NULL,
    created_at_utc DATETIME2(0) NOT NULL
        CONSTRAINT DF_chat_session_created DEFAULT SYSUTCDATETIME(),
    updated_at_utc DATETIME2(0) NOT NULL
        CONSTRAINT DF_chat_session_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_chat_session PRIMARY KEY (id),
    CONSTRAINT FK_chat_session_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
GO

CREATE INDEX IX_chat_session_user ON chat_session(user_id, updated_at_utc);
GO

CREATE TABLE chat_message (
    id BIGINT IDENTITY(1,1) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(10) NOT NULL,
    text NVARCHAR(2000) NOT NULL,
    intent VARCHAR(40) NULL,
    confidence DECIMAL(5,4) NULL,
    feedback VARCHAR(10) NULL,
    created_at_utc DATETIME2(0) NOT NULL
        CONSTRAINT DF_chat_message_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_chat_message PRIMARY KEY (id),
    CONSTRAINT FK_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id),
    CONSTRAINT CK_chat_message_role CHECK (role IN ('USER','BOT')),
    CONSTRAINT CK_chat_message_feedback CHECK (feedback IS NULL OR feedback IN ('UP','DOWN'))
);
GO

CREATE INDEX IX_chat_message_session ON chat_message(session_id, id);
GO

CREATE TABLE chat_training_candidate (
    id BIGINT IDENTITY(1,1) NOT NULL,
    message_id BIGINT NULL,
    text NVARCHAR(2000) NOT NULL,
    predicted_intent VARCHAR(40) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    label VARCHAR(40) NULL,
    status VARCHAR(20) NOT NULL
        CONSTRAINT DF_chat_cand_status DEFAULT 'PENDING',
    created_at_utc DATETIME2(0) NOT NULL
        CONSTRAINT DF_chat_cand_created DEFAULT SYSUTCDATETIME(),
    labeled_by_user_id BIGINT NULL,
    labeled_at_utc DATETIME2(0) NULL,
    CONSTRAINT PK_chat_training_candidate PRIMARY KEY (id),
    CONSTRAINT CK_chat_cand_status CHECK (status IN ('PENDING','LABELED','REJECTED'))
);
GO

CREATE INDEX IX_chat_cand_status ON chat_training_candidate(status, created_at_utc);
GO