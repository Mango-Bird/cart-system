# Bộ kiểm thử tải Cart System

## Phạm vi

Locust là máy phát tải HTTP; backend, MySQL và Redis chạy riêng. Không đổi Java, API hoặc authentication. Chỉ chạy trên hệ thống được phép kiểm thử. Notebook CPU không chứa JWT signing secret, không mở tunnel và không giả định inbound traffic vào Kaggle.

Python 3.12 được dùng để kiểm tra local. Cài vào virtualenv riêng:

```powershell
python -m venv load-testing/.venv
load-testing/.venv/Scripts/python -m pip install -r load-testing/requirements.txt
```

Dependencies trực tiếp được pin: Locust 2.46.7, nbformat 5.10.4, Matplotlib 3.10.8. Transitive dependencies chưa được khóa bằng hash; giữ `pip freeze` của mỗi môi trường để tái lập phép đo.

## Chuẩn bị dữ liệu ngoài thời gian đo

1. Chạy backend và nạp sản phẩm vào database test theo [Docker](../docs/docker.md). Product phải có stock >= 2, không thay tồn kho trong khi đo.
2. Đặt cấu hình, không lưu password/token vào notebook hoặc Git. `config.example.env` là mẫu tham khảo, script không tự load file này.

```powershell
$env:TARGET_BASE_URL = 'http://127.0.0.1:8081'
$env:LOAD_TEST_PASSWORD = Read-Host 'Password riêng cho tài khoản test'
$env:LOAD_TEST_RUN_ID = 'local_run_01'
$env:LOAD_TEST_DATA = 'load-testing/results/accounts.json'
load-testing/.venv/Scripts/python load-testing/prepare.py --users 100 --output $env:LOAD_TEST_DATA
```

`prepare.py` kiểm tra kết nối, chọn product ID nhỏ nhất có stock >= 2 (hoặc LOAD_TEST_PRODUCT_ID), tạo tài khoản `lt_<runId>_<index>`, login, tạo giỏ riêng và xóa item test cũ nếu có. Register 409 chỉ được chấp nhận khi login thành công bằng password đã cung cấp. Manifest chỉ lưu username, user ID, target và product ID, không có credentials/token. Không dùng một tài khoản cho nhiều virtual users. Chạy lại cùng run ID/password cho phép chuẩn bị lại dữ liệu; run ID mới tạo tài khoản mới và không tự xóa tài khoản cũ.

Chỉ chạy một process Locust. Không dùng `--worker`, `--master`, `--processes` hoặc nhiều phiên cùng manifest: mỗi process sẽ lấy trùng tài khoản. Khi đổi target hoặc tăng số users phải prepare lại. Không dùng tài khoản thật. Dữ liệu account vẫn còn trong database sau test; việc dọn dữ liệu cần làm trên database test riêng.

## Workload và kết quả kỳ vọng

| Task weight | Thao tác | Kết quả kỳ vọng |
|---:|---|---|
| 4 | GET /api/products | 200, JSON array |
| 3 | GET /api/products/{id} | 200, đúng product ID |
| 4 | GET /api/cart | 200, đúng userId và items array |
| 2 | POST item quantity 1 → PUT quantity 2 → DELETE item | Mỗi request 200; đúng userId và items array |

Mỗi user xử lý tuần tự, nghỉ ngẫu nhiên 0.5–1.5 giây giữa task. Weight là xác suất task, không phải tỷ lệ request vì write cycle có ba request. Product chung chỉ đọc stock; giỏ riêng tránh cạnh tranh trên cùng cart. Không benchmark checkout hoặc inventory reservation.

Login ở đầu mỗi user được tính vào metrics với tên AUTH login. Script đọc exp của token để lên lịch relogin trước 30 giây; server vẫn chịu trách nhiệm xác thực chữ ký. Nếu nhận 401, ghi nhận đó là failure, login lại và retry tối đa một lần. Tất cả 4xx/5xx và transport errors trong workload là lỗi thực sự; không đánh dấu 400/404/409 thành success để cải thiện số liệu. Register 409 kỳ vọng chỉ nằm trong bước prepare ngoài thời gian đo. Workload không gửi yêu cầu âm tính cố ý.

Nếu DELETE lỗi, user dừng và đánh dấu exit code lỗi để tránh thêm dồn item khi trạng thái chưa rõ. Phép đo gồm ramp-up, đăng nhập và các request hoàn tất trong graceful stop; không phải phép đo steady-state riêng.

## Profile và cách chạy

| Profile | Users | Spawn rate | Thời lượng yêu cầu |
|---|---:|---:|---|
| smoke | 1 | 1/s | 30s |
| low | 10 | 2/s | 2m |
| medium | 50 | 5/s | 3m |
| high | 100 | 10/s | 5m |

Đây là cấu hình đề xuất, không phải kết quả benchmark. Ví dụ:

```powershell
load-testing/.venv/Scripts/python load-testing/run.py --profile smoke --output load-testing/results/local-smoke
load-testing/.venv/Scripts/python load-testing/run.py --profile low --output load-testing/results/local-low
```

Có thể override `--users`, `--spawn-rate`, `--duration`. Mỗi output phải là thư mục rỗng, tránh trộn kết quả. Giới hạn subprocess là 1 giờ; stop timeout của Locust là 15 giây; HTTP timeout mặc định 10 giây, đổi qua LOAD_TEST_TIMEOUT. Tăng tải tuần tự sau smoke thành công, không tự chạy tất cả profile vào backend chưa được duyệt.

## Artifacts và cách đọc

- `final-stats.csv`: snapshot cuối sau graceful stop; nguồn cho total request, failure, RPS, average/median/p95/p99 trong summary. Đối chiếu tổng với http-status.csv.
- `locust_stats.csv`: snapshot CSV định kỳ của Locust; có thể thiếu vài request cuối so với final-stats.csv.
- `locust_stats_history.csv`: diễn biến thời gian, user count; `--csv-full-history` có thêm từng endpoint.
- `http-status.csv`: số request theo endpoint, HTTP status/transport_error và success/failure. HTTP 200 vẫn có thể failure nếu DTO không đúng.
- `run-metadata.json`: users cấu hình/đỉnh quan sát, spawn rate, runtime yêu cầu, thời gian thực tế.
- `summary.json`: metrics tổng hợp, phiên bản/cấu hình máy phát tải và nguồn đo local/Kaggle.
- `report.html`, `locust_failures.csv`, `locust_exceptions.csv`, `locust.log`: báo cáo và chẩn đoán.

CSV percentiles là ước lượng theo histogram của Locust, không phải trung bình percentiles từng endpoint. Failure rate = failure count / request count. RPS lấy từ thống kê tổng Locust; duration metadata có thể gồm graceful stop. Thư mục results bị Git ignore; rà soát artifacts trước khi chia sẻ, nhất là target và thông tin môi trường. Không xuất request/response body hoặc Authorization header.

## Kaggle CPU

Upload nguyên thư mục load-testing dưới dạng Kaggle Dataset riêng hoặc lấy từ revision đã duyệt; notebook cần các script đi kèm, không chỉ file ipynb. Notebook có biến SUITE_DIR trỏ đến thư mục này, copy script sang `/kaggle/working/cart-load-testing`, nhận password qua Kaggle Secrets `LOAD_TEST_PASSWORD` và target qua cấu hình. Accelerator chọn None/CPU, Internet phải được bật và kiểm tra thực tế.

`localhost` trong Kaggle thuộc máy Kaggle, không phải Windows hoặc Docker host của bạn. Dùng địa chỉ HTTPS backend đã được cho phép truy cập. Không public MySQL/Redis. Chạy notebook và tải artifacts về trước khi cập nhật [báo cáo](../docs/load-testing.md); notebook không tự chứng minh đã benchmark Kaggle chỉ vì có cell chạy Locust.

Tham khảo: [Locust event hooks](https://docs.locust.io/en/stable/extending-locust.html), [Kaggle Notebooks](https://www.kaggle.com/docs/notebooks), [Kaggle Secrets](https://github.com/Kaggle/kaggle-cli/blob/main/docs/kernels.md).
