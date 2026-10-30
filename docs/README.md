# Tài liệu Cart System - Phase 1

Bộ tài liệu mô tả source hiện tại, cách chạy và phạm vi kiểm chứng. Thông tin triển khai được đối chiếu với Controller, DTO, Service, adapters, cấu hình và test source; kết quả chạy test ghi riêng theo ngày.

## Mục lục và thứ tự đọc

1. [README dự án](../README.md): chức năng, kiến trúc, API tổng quan và các đề xuất cho Phase 2.
2. [Kiến trúc](architecture.md): phân tầng, Domain, Repository, JWT, Cache-Aside và các quyết định thiết kế.
3. [REST API](api.md): chín endpoint, DTO, authentication, quy tắc quantity/stock và lỗi thực tế.
4. [Kiểm thử](testing.md): automated tests, môi trường, kết quả kiểm chứng và kịch bản manual/Redis.
5. [Docker](docker.md): đóng gói, cấu hình Compose, dữ liệu thử và persistence.
6. [Load testing](load-testing.md): Locust, Kaggle CPU, kết quả đã chạy và giới hạn phép đo.
7. [Chạy Kaggle CPU thực tế](kaggle-benchmark.md): phương án HTTPS, checklist từng bước, export và điều kiện xác nhận kết quả.

## Phạm vi đã triển khai

- Domain và Business services độc lập framework, Repository/Security/Cache abstractions.
- Presentation Controller, DTO, exception handler; API auth/product/cart/health.
- JPA entities/adapters, BCrypt, JWT filter, SecurityFilterChain, Redis cache và Spring bean wiring.
- OpenAPI Bearer scheme cùng tests Business, Presentation, JWT/Security, OpenAPI và context.
