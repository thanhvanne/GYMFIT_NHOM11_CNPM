/*
    GYMFIT - cho phép một hội viên sở hữu nhiều gói đang hoạt động.

    Chạy một lần trên database đã được tạo từ phiên bản cũ. File
    database/gymfit.sql đã được cập nhật để database tạo mới không tạo unique
    index này nữa.
*/

IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_membership_one_active_per_member'
      AND object_id = OBJECT_ID(N'dbo.membership')
)
BEGIN
    DROP INDEX UX_membership_one_active_per_member
        ON dbo.membership;
END;
GO
