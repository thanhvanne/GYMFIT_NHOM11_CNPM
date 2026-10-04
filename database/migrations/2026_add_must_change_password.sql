-- =====================================================================
-- Thêm cột app_user.must_change_password (yêu cầu đổi mật khẩu lần đầu)
-- Áp dụng cho DB đã tạo sẵn trước đó (DDL mới nằm trong gymfit.sql).
-- An toàn khi chạy lại nhiều lần (IF COL_LENGTH ... IS NULL).
--
-- Cách chạy:
--   sqlcmd -C -S localhost -U sa -P 123 -d gymfit -i database/migrations/2026_add_must_change_password.sql
-- =====================================================================

-- sqlcmd (ODBC) mặc định QUOTED_IDENTIFIER OFF → ALTER TABLE sẽ bị từ chối.
SET QUOTED_IDENTIFIER ON;
GO
SET ANSI_NULLS ON;
GO

IF COL_LENGTH('app_user', 'must_change_password') IS NULL
    ALTER TABLE app_user
        ADD must_change_password BIT NOT NULL
            CONSTRAINT DF_app_user_must_change_password DEFAULT 0;
GO

-- Kiểm tra: mọi hàng cũ đều mang giá trị 0 (DEFAULT áp cho hàng đã có).
SELECT
    COL_LENGTH('app_user', 'must_change_password') AS col_len,
    SUM(CASE WHEN must_change_password = 1 THEN 1 ELSE 0 END) AS bat_doi_mat_khau,
    COUNT(*) AS tong
FROM app_user;
GO
