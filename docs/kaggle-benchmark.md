# Hướng dẫn và báo cáo Kaggle CPU benchmark

Ngày cập nhật tài liệu: 29/10/2026. Hướng dẫn phản ánh notebook và scripts hiện có. Không chạy benchmark mới trong lần viết tài liệu này.

## 1. Phân biệt thành phần và trạng thái

- Kaggle CPU là máy phát tải Locust.
- Backend Spring Boot, MySQL và Redis là hệ thống được kiểm thử ở môi trường khác.
- READY TO RUN: tooling và cấu hình đã chuẩn bị, chưa đồng nghĩa đã phát tải.
- EXECUTED: đã có log và artifacts của lượt chạy thực tế, dù có thể thất bại.
- VERIFIED: đã review runtime Kaggle CPU, target được duyệt và dữ liệu đo nhất quán.

Notebook trong Git là template chưa có execution/output. Trạng thái NOT RUN trong template không phủ nhận kết quả lịch sử đã lưu riêng.

## 2. Điều kiện trước khi chạy

1. Có backend test HTTPS được phép nhận tải; chốt users, thời lượng và ngưỡng dừng trước khi chạy.
2. Backend có sản phẩm với stock ít nhất 2, cho phép đăng ký tài khoản test và thao tác giỏ bằng JWT.
3. Có tài khoản Kaggle, notebook CPU và quyền bật Internet. Nếu Kaggle yêu cầu xác minh tài khoản, chủ tài khoản tự hoàn tất.
4. Có Secret `LOAD_TEST_PASSWORD` dùng riêng cho tài khoản thử nghiệm và cấp quyền cho notebook.
5. Có bộ scripts sạch đúng revision. Không upload .env, virtualenv, accounts, kết quả cũ hay secret backend.

Không tự tạo tunnel, thay firewall hoặc mở database/Redis ra Internet. Nếu chưa có quyền truy cập hoặc backend HTTPS hợp lệ, trạng thái lượt mới vẫn là NOT RUN.

## 3. Chuẩn bị scripts và notebook

Tạo Dataset private chứa thư mục `load-testing/` với các file:

```text
load-testing/
  requirements.txt
  tooling.py
  check_connectivity.py
  prepare.py
  locustfile.py
  run.py
  report.py
```

Import `load-testing/kaggle_load_test.ipynb` vào Kaggle Notebook và attach Dataset. Notebook cần các script đi kèm, không chỉ file ipynb. Chỉnh `SUITE_DIR` theo đúng đường dẫn Dataset hiển thị trong Kaggle; mặc định template là `/kaggle/input/cart-system-load-testing/load-testing`.

Notebook copy scripts sang `/kaggle/working/cart-load-testing`, rồi cài requirements bằng Python của kernel. Python cần ít nhất 3.11 theo kiểm tra trong notebook; runtime lịch sử là 3.13.15. Dependencies trực tiếp được pin trong requirements, dependency gián tiếp chưa khóa toàn bộ bằng hash.

## 4. Cấu hình từng bước

### Bước 1 - Chọn CPU và Internet

Trong settings của notebook, chọn Accelerator=None và bật Internet. Kiểm tra trạng thái thực tế trước khi xác nhận `CPU_CONFIRMED=True`. Không chỉ đổi biến để bỏ qua kiểm tra.

### Bước 2 - Cấp Secret

Thêm Secret tên `LOAD_TEST_PASSWORD`, cấp quyền cho notebook. Không điền giá trị vào code, markdown hoặc output. Notebook lấy Secret qua `UserSecretsClient`, không fallback sang prompt nếu không đọc được.

JWT được lấy bằng login API cho từng tài khoản. Kaggle không cần JWT signing secret và không cần credentials MySQL/Redis.

### Bước 3 - Điền biến cấu hình

```python
TARGET_BASE_URL = 'https://<backend-test-domain>'
CPU_CONFIRMED = True
SOURCE_REVISION = '<revision-cua-bo-script-da-upload>'
BACKEND_DESCRIPTION = '<revision-va-moi-truong-backend-neu-biet>'
PROFILE = 'smoke'
APPROVED_MAX_USERS = 1
```

URL phải là HTTPS origin, không thêm `/api`, query, fragment hoặc credentials. Mẫu domain ở trên phải được thay bằng địa chỉ thực. `SOURCE_REVISION` là revision của generator; không dùng nó thay cho backend revision.

### Bước 4 - Connectivity check

Chạy các cell theo thứ tự tới connectivity check. Script kiểm tra public DNS, TLS, health/products, timeout hữu hạn và không follow redirect.

Lệnh tương đương trong runtime, sau khi đã cấu hình environment:

```bash
python /kaggle/working/cart-load-testing/check_connectivity.py --output /kaggle/working/cart-load-testing/connectivity.json
```

Nếu lỗi DNS, Internet, TLS, URL hết hạn hoặc thiếu product phù hợp, dừng và sửa điều kiện môi trường. Không tắt TLS verification để làm preflight pass.

### Bước 5 - Chạy Smoke

Notebook sinh run ID riêng, lưu environment evidence và chuẩn bị một tài khoản/cart trước thời gian đo. Sau đó gọi Locust headless qua `run.py`: 1 user, spawn 1/s, thời lượng cấu hình 30 giây.

Không nhấn Run All nhiều lần khi lượt trước chưa kết thúc. Xem `execution.json`, exit code, failures và log. Nếu run FAILED hoặc INCOMPLETE, giữ artifacts để điều tra và không tăng tải.

### Bước 6 - Low, Medium hoặc High khi được phép

Profile có sẵn: Low 10 users/2 phút, Medium 50 users/3 phút, High 100 users/5 phút. Chỉ đổi `PROFILE` và giới hạn được duyệt sau khi kiểm tra Smoke và được chủ backend cho phép mức mới. Notebook kiểm tra số users của profile không vượt `APPROVED_MAX_USERS`.

Trong phiên lịch sử chỉ được duyệt Smoke. Không xem hướng dẫn này là quyền chạy các mức tải lớn hơn.

### Bước 7 - Tổng hợp và export

Chạy cell tổng hợp, vẽ history và export sau workload, kể cả khi workload lỗi nếu runtime còn hoạt động. ZIP chứa results, evidence và analysis; không chứa manifest accounts hoặc environment secrets theo luồng export hiện tại.

Tải `cart-load-report-<RUN_ID>.zip` trong Output của Kaggle. Rà soát URL, metadata và log trước khi chia sẻ; không tự public notebook hoặc ZIP. Lưu artifacts trước khi dừng session vì dữ liệu working có thể mất.

## 5. Failure và timeout handling

- HTTP timeout mặc định 10 giây, cho phép cấu hình trong khoảng lớn hơn 0 đến 120 giây.
- Runner giới hạn process theo duration cộng 60 giây; interruption/timeout được ghi nhận trong execution nếu runner xử lý được sự kiện.
- Nếu toàn bộ runtime bị hủy đột ngột, có thể thiếu file cuối; không đánh dấu run hoàn tất chỉ từ output cũ.
- Login/relogin nằm trong metrics. HTTP 401 đầu tiên vẫn là failure dù lần retry thành công.
- DELETE lỗi khiến user dừng để tránh cộng dồn item khi trạng thái chưa rõ.
- Khi thiếu hoặc lệch counters, report đánh dấu artifacts không hợp lệ; không tạo dữ liệu thay thế.

## 6. Kết quả lịch sử đã kiểm chứng

Ngày 15/10/2026, sau khi bật Internet và khôi phục notebook, đã có hai lượt Smoke Kaggle CPU. Kết quả chi tiết ở [báo cáo load testing](load-testing.md).

| Run | Requests | RPS | Avg (ms) | p95 (ms) | p99 (ms) | Failures |
|---|---:|---:|---:|---:|---:|---:|
| smoke-20261015T042407-d168fdd5 | 23 | 0.7729 | 406.19 | 760 | 790 | 0 |
| smoke-20261015T042451-1c63356a | 24 | 0.8047 | 526.64 | 870 | 1200 | 0 |

- Mỗi lượt: 1 user, spawn 1/s, cấu hình 30 giây, exit code 0 và execution COMPLETED.
- Runtime evidence: Kaggle Interactive CPU, Linux, 4 logical CPUs, RAM 33,658,318,848 byte, Python 3.13.15, Locust 2.46.7.
- Preflight từ Kaggle PASS; workload tất cả HTTP 200, tổng 47 requests, 0 failures.
- Source generator ghi `f384e4c+workingtree-e00b603dd34a`, tức snapshot working tree lúc chạy; backend revision ghi unknown.
- Raw summaries có nhãn NOT VERIFIED do tool sinh mặc định. Kết luận VERIFIED lịch sử dựa trên review UI, môi trường và artifacts tải về, không sửa nhãn raw để tự chứng nhận.
- Low/Medium/High trên Kaggle: NOT RUN. Cache hit/miss: NOT MEASURED.

ZIP lịch sử: `cart-load-report-20261015T042451-1c63356a.zip`.

SHA256 archive đã tải: `97eecf71737b0e2f5503c98e83a467327da49c511b33002594630e7f004b9ee9`.

Artifacts giải nén local nằm ở `load-testing/results/kaggle-20261015/`, gồm hai run, evidence, HTML/CSV và ảnh `kaggle-smoke-proof.jpg`. Thư mục này bị Git ignore và không đi cùng clone mới. Cần người giữ archive cung cấp riêng nếu muốn kiểm tra lại trên máy khác.

## 7. Tiêu chí review kết quả

1. Xác nhận settings CPU/Internet và evidence runtime thật; không chỉ dựa vào LOAD_TEST_ORIGIN.
2. Xác nhận target, mức tải và thời gian nằm trong phạm vi được duyệt.
3. Đọc execution, log, final-stats.csv, http-status.csv, run-metadata.json và summary.json.
4. Đối chiếu request/failure counts, RPS, latency và script hashes. Không cộng RPS hoặc trung bình percentile giữa các run.
5. Ghi backend revision unknown nếu không có bằng chứng; không tự gán revision.
6. Tách lỗi hệ thống, môi trường và tooling. Chỉ đánh dấu VERIFIED cho phần đã đủ bằng chứng.

Hai lượt Smoke chứng minh luồng login/read/write hoạt động từ Kaggle qua HTTPS ở tải nhỏ. Chưa đủ để kết luận chịu được lượng truy cập sale, xác định bottleneck, lợi ích riêng của Redis hoặc khả năng HA.

Quay lại [mục lục](README.md), [hướng dẫn bộ phát tải](../load-testing/README.md), [Docker](docker.md).
