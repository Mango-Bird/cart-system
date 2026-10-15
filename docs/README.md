# Tài liệu Cart System — Phase 1

Bộ tài liệu mô tả source hiện tại, cách chạy và phạm vi kiểm chứng. Thông tin triển khai được đối chiếu với Controller, DTO, Service, adapters, cấu hình và test source; kết quả chạy test ghi riêng theo ngày.

## Mục lục và thứ tự đọc

1. [README dự án](../README.md): chức năng, kiến trúc, luồng xử lý và giới hạn Phase 1.
2. [Kiến trúc](architecture.md): phân tầng, Domain, Repository, JWT, Cache-Aside và các quyết định thiết kế.
3. [REST API](api.md): chín endpoint, DTO, authentication, quy tắc quantity/stock và lỗi thực tế.
4. [Kiểm thử](testing.md): automated tests, môi trường, kết quả kiểm chứng và kịch bản manual/Redis.
5. [Docker](docker.md): đóng gói, cấu hình Compose, dữ liệu thử và persistence.
6. [Load testing](load-testing.md): Locust, Kaggle CPU, kết quả đã chạy và giới hạn phép đo.

## Phạm vi đã triển khai

- Domain và Business services độc lập framework, Repository/Security/Cache abstractions.
- Presentation Controller, DTO, exception handler; API auth/product/cart/health.
- JPA entities/adapters, BCrypt, JWT filter, SecurityFilterChain, Redis cache và Spring bean wiring.
- OpenAPI Bearer scheme cùng tests Business, Presentation, JWT/Security, OpenAPI và context.

## Phạm vi chưa hoàn tất hoặc chưa xác nhận

Test với MockMvc/Mockito không thay thế kiểm thử MySQL/MariaDB và Redis thật. Kết quả ngày 12/10/2026 trong testing.md là lịch sử, không phải trạng thái kiểm chứng mới nhất. Cấu hình Docker Compose, seed sản phẩm chủ động và bộ Locust/Kaggle được mô tả trong hai tài liệu mới; xem load-testing.md để biết những gì đã thực sự chạy. Chưa có tests persistence/Redis riêng, migrations, benchmark Kaggle thực tế, refresh/revoke token, frontend, order/payment/checkout hoặc triển khai Nginx.

Các hướng phát triển trong tài liệu là đề xuất, không phải tính năng đã có. Bộ tài liệu không khẳng định hệ thống đủ điều kiện vận hành production.
