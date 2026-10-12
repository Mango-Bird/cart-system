# Tài liệu Cart System — Phase 1

Bộ tài liệu mô tả source hiện tại, cách chạy và phạm vi kiểm chứng. Thông tin triển khai được đối chiếu với Controller, DTO, Service, adapters, cấu hình và test source; kết quả chạy test ghi riêng theo ngày.

## Mục lục và thứ tự đọc

1. [README dự án](../README.md): phạm vi, công nghệ, chuẩn bị database/Redis, biến môi trường và Swagger.
2. [Kiến trúc](architecture.md): phân tầng, Domain, Repository, JWT, Cache-Aside và các quyết định thiết kế.
3. [REST API](api.md): chín endpoint, DTO, authentication, quy tắc quantity/stock và lỗi thực tế.
4. [Kiểm thử](testing.md): automated tests, môi trường, kết quả kiểm chứng và kịch bản manual/Redis.

## Phạm vi đã triển khai

- Domain và Business services độc lập framework, Repository/Security/Cache abstractions.
- Presentation Controller, DTO, exception handler; API auth/product/cart/health.
- JPA entities/adapters, BCrypt, JWT filter, SecurityFilterChain, Redis cache và Spring bean wiring.
- OpenAPI Bearer scheme cùng tests Business, Presentation, JWT/Security, OpenAPI và context.

## Phạm vi chưa hoàn tất hoặc chưa xác nhận

Test với MockMvc/Mockito không thay thế kiểm thử MySQL/MariaDB và Redis thật. Lần chạy ngày 12/10/2026 còn lỗi database ở context test. Chưa có tests persistence/Redis riêng, migrations, seed sản phẩm, benchmark, refresh/revoke token, frontend, order/payment/checkout hoặc triển khai Nginx/Docker Compose trong repository.

Các hướng phát triển trong tài liệu là đề xuất, không phải tính năng đã có. Bộ tài liệu không khẳng định hệ thống đủ điều kiện vận hành production.
