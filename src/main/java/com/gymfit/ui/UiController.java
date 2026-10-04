package com.gymfit.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Trang đổi mật khẩu bắt buộc (F7) – dùng cho tài khoản
     * {@code mustChangePassword=true} sau lần đăng nhập đầu tiên.
     */
    @GetMapping("/change-password")
    public String changePassword() {
        return "change-password";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/branches")
    public String adminBranches() {
        return "admin/branches";
    }

    @GetMapping("/admin/facilities")
    public String adminFacilities() {
        return "admin/facilities";
    }

    @GetMapping("/admin/members")
    public String adminMembers() {
        return "admin/members";
    }

    @GetMapping("/admin/plans")
    public String adminPlans() {
        return "admin/plans";
    }

    @GetMapping("/admin/sales")
    public String adminSales() {
        return "admin/sales";
    }

    @GetMapping("/admin/inventory")
    public String adminInventory() {
        return "admin/inventory";
    }

    @GetMapping("/admin/bookings")
    public String adminBookings() {
        return "admin/bookings";
    }

    @GetMapping("/admin/checkins")
    public String adminCheckIns() {
        return "admin/checkins";
    }

    @GetMapping("/admin/reports")
    public String adminReports() {
        return "admin/reports";
    }

    @GetMapping("/admin/users")
    public String adminUsers() {
        return "admin/users";
    }

    /**
     * Trang quản trị chatbot (V2-1): gán nhãn câu hỏi bot trả lời chưa chắc
     * chắn, xem thống kê và export {@code seed_from_logs.jsonl}.
     */
    @GetMapping("/admin/chatbot")
    public String adminChatbot() {
        return "admin/chatbot";
    }

    @GetMapping("/manager/dashboard")
    public String managerDashboard() {
        return "manager/dashboard";
    }

    @GetMapping("/manager/members")
    public String managerMembers() {
        return "manager/members";
    }

    @GetMapping("/manager/plans")
    public String managerPlans() {
        return "manager/plans";
    }

    @GetMapping("/manager/sales")
    public String managerSales() {
        return "manager/sales";
    }

    @GetMapping("/manager/bookings")
    public String managerBookings() {
        return "manager/bookings";
    }

    @GetMapping("/manager/checkins")
    public String managerCheckIns() {
        return "manager/checkins";
    }

    @GetMapping("/member/home")
    public String memberHome() {
        return "member/home";
    }

    @GetMapping("/member/booking")
    public String memberBooking() {
        return "member/booking";
    }

    @GetMapping("/member/qr")
    public String memberQr() {
        return "member/qr";
    }

    @GetMapping("/member/history")
    public String memberHistory() {
        return "member/history";
    }

    @GetMapping("/member/profile")
    public String memberProfile() {
        return "member/profile";
    }

    @GetMapping("/member/plans")
    public String memberPlans() {
        return "member/plans";
    }
}