# Báo cáo huấn luyện chatbot GYMFIT

- Số mẫu train: 18141
- Số mẫu val: 3739
- Số mẫu test: 3715
- Số intent: 31
- Số feature: 10258
- Siêu tham số: lr0=0.1, l2=1.00000e-05, epochs=40

### VAL (sinh tự động — dùng để chọn siêu tham số)

- Accuracy: **0.9741**
- Macro-F1: **0.9505**
- Recall OUT_OF_SCOPE: **0.9593**
- Số mẫu: 31 intent có nhãn thật

| Intent | Precision | Recall | F1 |
|---|---|---|---|
| AUDIT_RECENT | 0.9200 | 0.9583 | 0.9388 |
| BOOKINGS_TODAY | 0.7857 | 0.7333 | 0.7586 |
| BOOKING_AVAILABILITY | 0.9967 | 1.0000 | 0.9984 |
| BOOKING_CANCEL | 0.9774 | 1.0000 | 0.9886 |
| BOOKING_CREATE | 0.9750 | 1.0000 | 0.9873 |
| BRANCH_INFO | 0.9388 | 0.9928 | 0.9650 |
| CHECKINS_REJECTED | 0.9844 | 1.0000 | 0.9921 |
| CONFIRM_NO | 1.0000 | 0.9693 | 0.9844 |
| CONFIRM_YES | 0.9928 | 0.9857 | 0.9892 |
| FAQ_GENERAL | 0.9858 | 0.9760 | 0.9809 |
| GOODBYE | 1.0000 | 0.9759 | 0.9878 |
| GREETING | 1.0000 | 1.0000 | 1.0000 |
| HELP | 1.0000 | 0.9400 | 0.9691 |
| LIST_FACILITIES | 1.0000 | 0.9600 | 0.9796 |
| LIST_PLANS | 0.9008 | 0.9083 | 0.9046 |
| LIST_PRODUCTS | 0.9778 | 0.9167 | 0.9462 |
| LIST_SERVICES | 1.0000 | 0.9375 | 0.9677 |
| LOW_STOCK | 1.0000 | 1.0000 | 1.0000 |
| MY_BOOKINGS | 0.9907 | 0.9907 | 0.9907 |
| MY_CHECKINS | 1.0000 | 0.9394 | 0.9688 |
| MY_MEMBERSHIP | 0.9231 | 0.9231 | 0.9231 |
| MY_ORDERS | 1.0000 | 0.9429 | 0.9706 |
| OPERATING_HOURS | 1.0000 | 0.9787 | 0.9892 |
| OUT_OF_SCOPE | 0.9478 | 0.9593 | 0.9535 |
| PLAN_COMPARE | 0.8788 | 0.8788 | 0.8788 |
| PLAN_DETAIL | 0.9396 | 0.9589 | 0.9492 |
| PLAN_RECOMMEND | 0.9286 | 1.0000 | 0.9630 |
| REPORT_DASHBOARD | 0.9643 | 0.9310 | 0.9474 |
| REPORT_REVENUE | 0.9936 | 1.0000 | 0.9968 |
| REPORT_SERVICE | 0.9516 | 0.9752 | 0.9633 |
| THANKS | 0.4615 | 1.0000 | 0.6316 |

**Top cặp nhầm lẫn**

- LIST_PLANS → PLAN_DETAIL: 8
- LIST_SERVICES → BRANCH_INFO: 5
- OUT_OF_SCOPE → FAQ_GENERAL: 4
- CONFIRM_YES → THANKS: 4
- PLAN_DETAIL → LIST_PLANS: 4
- LIST_PRODUCTS → OUT_OF_SCOPE: 4
- OUT_OF_SCOPE → BRANCH_INFO: 3
- CONFIRM_NO → THANKS: 3
- FAQ_GENERAL → BOOKING_CANCEL: 3
- FAQ_GENERAL → OUT_OF_SCOPE: 3
### TEST (sinh tự động)

- Accuracy: **0.9650**
- Macro-F1: **0.9465**
- Recall OUT_OF_SCOPE: **0.7708**
- Số mẫu: 31 intent có nhãn thật

| Intent | Precision | Recall | F1 |
|---|---|---|---|
| AUDIT_RECENT | 0.9880 | 0.9880 | 0.9880 |
| BOOKINGS_TODAY | 0.9884 | 0.9659 | 0.9770 |
| BOOKING_AVAILABILITY | 0.9613 | 1.0000 | 0.9803 |
| BOOKING_CANCEL | 0.9348 | 0.9556 | 0.9451 |
| BOOKING_CREATE | 0.9662 | 1.0000 | 0.9828 |
| BRANCH_INFO | 0.9659 | 0.9827 | 0.9742 |
| CHECKINS_REJECTED | 1.0000 | 0.9933 | 0.9967 |
| CONFIRM_NO | 0.9661 | 0.9500 | 0.9580 |
| CONFIRM_YES | 0.9808 | 0.9444 | 0.9623 |
| FAQ_GENERAL | 0.9646 | 0.9740 | 0.9693 |
| GOODBYE | 1.0000 | 1.0000 | 1.0000 |
| GREETING | 0.9894 | 0.9738 | 0.9815 |
| HELP | 1.0000 | 0.8182 | 0.9000 |
| LIST_FACILITIES | 0.9843 | 0.9766 | 0.9804 |
| LIST_PLANS | 0.9091 | 0.8197 | 0.8621 |
| LIST_PRODUCTS | 0.9286 | 1.0000 | 0.9630 |
| LIST_SERVICES | 0.9717 | 0.9877 | 0.9796 |
| LOW_STOCK | 1.0000 | 0.9652 | 0.9823 |
| MY_BOOKINGS | 0.9737 | 0.9024 | 0.9367 |
| MY_CHECKINS | 0.8750 | 1.0000 | 0.9333 |
| MY_MEMBERSHIP | 0.9636 | 0.9815 | 0.9725 |
| MY_ORDERS | 0.9600 | 0.9796 | 0.9697 |
| OPERATING_HOURS | 1.0000 | 0.9854 | 0.9926 |
| OUT_OF_SCOPE | 0.8315 | 0.7708 | 0.8000 |
| PLAN_COMPARE | 0.9789 | 0.9490 | 0.9637 |
| PLAN_DETAIL | 0.8889 | 1.0000 | 0.9412 |
| PLAN_RECOMMEND | 0.9778 | 0.9072 | 0.9412 |
| REPORT_DASHBOARD | 0.8000 | 0.5000 | 0.6154 |
| REPORT_REVENUE | 1.0000 | 1.0000 | 1.0000 |
| REPORT_SERVICE | 0.9375 | 0.9868 | 0.9615 |
| THANKS | 0.9254 | 0.9394 | 0.9323 |

**Top cặp nhầm lẫn**

- OUT_OF_SCOPE → FAQ_GENERAL: 12
- LIST_PLANS → PLAN_DETAIL: 7
- FAQ_GENERAL → BOOKING_CANCEL: 5
- PLAN_RECOMMEND → LIST_SERVICES: 5
- FAQ_GENERAL → MY_CHECKINS: 4
- PLAN_COMPARE → LIST_PLANS: 4
- PLAN_RECOMMEND → PLAN_DETAIL: 4
- BOOKING_CANCEL → FAQ_GENERAL: 4
- REPORT_DASHBOARD → BRANCH_INFO: 4
- GREETING → THANKS: 3
### HOLDOUT (viết tay — KPI thật)

- Accuracy: **0.8884**
- Macro-F1: **0.8877**
- Recall OUT_OF_SCOPE: **1.0000**
- Số mẫu: 31 intent có nhãn thật

| Intent | Precision | Recall | F1 |
|---|---|---|---|
| AUDIT_RECENT | 1.0000 | 0.8333 | 0.9091 |
| BOOKINGS_TODAY | 0.6667 | 0.8000 | 0.7273 |
| BOOKING_AVAILABILITY | 0.8750 | 1.0000 | 0.9333 |
| BOOKING_CANCEL | 1.0000 | 0.8571 | 0.9231 |
| BOOKING_CREATE | 1.0000 | 1.0000 | 1.0000 |
| BRANCH_INFO | 0.5714 | 0.6667 | 0.6154 |
| CHECKINS_REJECTED | 1.0000 | 1.0000 | 1.0000 |
| CONFIRM_NO | 1.0000 | 1.0000 | 1.0000 |
| CONFIRM_YES | 1.0000 | 1.0000 | 1.0000 |
| FAQ_GENERAL | 0.9667 | 0.8056 | 0.8788 |
| GOODBYE | 1.0000 | 1.0000 | 1.0000 |
| GREETING | 1.0000 | 0.5000 | 0.6667 |
| HELP | 0.8000 | 0.8000 | 0.8000 |
| LIST_FACILITIES | 1.0000 | 0.8000 | 0.8889 |
| LIST_PLANS | 0.7143 | 0.8333 | 0.7692 |
| LIST_PRODUCTS | 0.7143 | 1.0000 | 0.8333 |
| LIST_SERVICES | 0.7500 | 0.6000 | 0.6667 |
| LOW_STOCK | 1.0000 | 0.8000 | 0.8889 |
| MY_BOOKINGS | 1.0000 | 0.8000 | 0.8889 |
| MY_CHECKINS | 1.0000 | 1.0000 | 1.0000 |
| MY_MEMBERSHIP | 1.0000 | 1.0000 | 1.0000 |
| MY_ORDERS | 1.0000 | 0.8000 | 0.8889 |
| OPERATING_HOURS | 0.8333 | 0.8333 | 0.8333 |
| OUT_OF_SCOPE | 0.7879 | 1.0000 | 0.8814 |
| PLAN_COMPARE | 1.0000 | 1.0000 | 1.0000 |
| PLAN_DETAIL | 1.0000 | 0.8000 | 0.8889 |
| PLAN_RECOMMEND | 0.8333 | 1.0000 | 0.9091 |
| REPORT_DASHBOARD | 1.0000 | 1.0000 | 1.0000 |
| REPORT_REVENUE | 1.0000 | 1.0000 | 1.0000 |
| REPORT_SERVICE | 0.6667 | 0.8000 | 0.7273 |
| THANKS | 1.0000 | 1.0000 | 1.0000 |

**Top cặp nhầm lẫn**

- FAQ_GENERAL → OUT_OF_SCOPE: 5
- LIST_SERVICES → BRANCH_INFO: 2
- FAQ_GENERAL → LIST_PLANS: 2
- GREETING → OUT_OF_SCOPE: 1
- HELP → OUT_OF_SCOPE: 1
- MY_BOOKINGS → BOOKINGS_TODAY: 1
- MY_ORDERS → LIST_PRODUCTS: 1
- BOOKING_CANCEL → FAQ_GENERAL: 1
- BRANCH_INFO → HELP: 1
- BRANCH_INFO → OPERATING_HOURS: 1
### HOLDOUT theo tier

| Nhóm | Số mẫu | Accuracy | Macro-F1 | Sai |
|---|---:|---:|---:|---:|
| ADVERSARIAL | 5 | 1.0000 | 1.0000 | 0 |
| EASY | 85 | 0.8706 | 0.7786 | 11 |
| NATURAL | 125 | 0.8960 | 0.9178 | 13 |

### HOLDOUT theo tag

| Nhóm | Số mẫu | Accuracy | Macro-F1 | Sai |
|---|---:|---:|---:|---:|
| greeting_wrapper | 1 | 1.0000 | 1.0000 | 0 |
| long | 14 | 1.0000 | 1.0000 | 0 |
| no_diacritics | 5 | 1.0000 | 1.0000 | 0 |
| oos | 26 | 1.0000 | 1.0000 | 0 |
| security | 7 | 1.0000 | 1.0000 | 0 |
| short | 47 | 0.8936 | 0.8263 | 5 |
| symbols | 3 | 1.0000 | 1.0000 | 0 |
| tier_adversarial | 5 | 1.0000 | 1.0000 | 0 |
| tier_easy | 85 | 0.8706 | 0.7786 | 11 |
| tier_natural | 125 | 0.8960 | 0.9178 | 13 |
| typo | 1 | 1.0000 | 1.0000 | 0 |

### HOLDOUT — danh sách câu sai (24)

- e ơi → OUT_OF_SCOPE (cần GREETING)
- giới thiệu cho tôi biết về GYMFIT → OUT_OF_SCOPE (cần HELP)
- lịch tôi tuần này có bao nhiêu buổi → BOOKINGS_TODAY (cần MY_BOOKINGS)
- tôi thanh toán bằng phương thức nào → LIST_PRODUCTS (cần MY_ORDERS)
- tôi không thể đến tập, huỷ lịch giúp tôi → FAQ_GENERAL (cần BOOKING_CANCEL)
- GYMFIT có những chi nhánh nào → HELP (cần BRANCH_INFO)
- chi nhánh thủ đức hoạt động không → OPERATING_HOURS (cần BRANCH_INFO)
- chi nhánh nào mở cửa sớm nhất → BRANCH_INFO (cần OPERATING_HOURS)
- bình thạnh có boxing không → BRANCH_INFO (cần LIST_SERVICES)
- chi nhánh thủ đức hỗ trợ dịch vụ nào → BRANCH_INFO (cần LIST_SERVICES)
- danh sách sân pickleball của GYMFIT → LIST_PRODUCTS (cần LIST_FACILITIES)
- gói nào rẻ nhất → PLAN_RECOMMEND (cần LIST_PLANS)
- gói vip có dịch vụ nào → LIST_SERVICES (cần PLAN_DETAIL)
- quét xong rồi tôi tập luôn hay phải chờ → OUT_OF_SCOPE (cần FAQ_GENERAL)
- thanh toán gói tập bằng cách nào → LIST_PLANS (cần FAQ_GENERAL)
- mua xong gói có được dùng ngay không → OUT_OF_SCOPE (cần FAQ_GENERAL)
- nút mua gói nằm ở trang nào → OUT_OF_SCOPE (cần FAQ_GENERAL)
- gia hạn gói tập ra sao → LIST_PLANS (cần FAQ_GENERAL)
- tại sao lại báo chưa có gói tập → OUT_OF_SCOPE (cần FAQ_GENERAL)
- MEMBERSHIP_BRANCH_MISMATCH là gì → OUT_OF_SCOPE (cần FAQ_GENERAL)
- lịch hôm nay theo từng môn → REPORT_SERVICE (cần BOOKINGS_TODAY)
- so sánh boxing với gym trong 7 ngày qua → BOOKING_AVAILABILITY (cần REPORT_SERVICE)
- món nào đang thấp nhất → REPORT_SERVICE (cần LOW_STOCK)
- lịch sử đăng nhập hôm nay → BOOKINGS_TODAY (cần AUDIT_RECENT)

