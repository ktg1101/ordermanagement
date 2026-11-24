# Order Management – Test Console (React + TypeScript)

Giao diện nhỏ để thử tay REST API của backend `ordermanagement` (`/api/v1`). Không có đăng nhập, không có CORS cần cấu hình: Vite dev server proxy `/api` và `/actuator` sang backend.

## Chạy

Yêu cầu: Node.js `^20.19` hoặc `>=22.12`, và backend đang chạy ở `http://localhost:8080`.

```bash
cd frontend
npm install
npm run dev
```

Mở http://localhost:5173. Góc phải trên có nhãn **Backend: UP** khi proxy nối được backend.

Backend ở địa chỉ khác:

```bash
# Linux/macOS
VITE_API_TARGET=http://localhost:9090 npm run dev
# Windows PowerShell
$env:VITE_API_TARGET="http://localhost:9090"; npm run dev
```

(hoặc copy `.env.example` thành `.env`).

Các lệnh khác: `npm run typecheck`, `npm run build` (kiểm tra kiểu + build ra `dist/`), `npm run preview`.

## Chạy bằng Docker

Từ thư mục gốc dự án, một lệnh chạy cả PostgreSQL + backend + giao diện:

```bash
cp .env.example .env
docker compose up --build
```

Mở http://localhost:3000 (đổi bằng `FRONTEND_PORT`). Container `frontend` dùng nginx phục vụ bản build và proxy `/api`, `/actuator` sang service `app`, nên không cần cấu hình CORS.

## Các tab

| Tab | Dùng để |
| --- | --- |
| **Kịch bản test nhanh** | Chạy sẵn 9 kịch bản nghiệp vụ end-to-end, hiện ✓/✗ từng bước: luồng thành công, thiếu tồn kho, hủy đơn đã thanh toán (hoàn tiền), hủy đơn chưa thanh toán, thanh toán lỗi rồi thử lại, Idempotency-Key, 12 request đặt hàng đồng thời trên kho 5, validation/404, quy tắc 422/409. |
| **Users** | Tạo, tìm, sửa tên, khóa/mở khóa user. |
| **Sản phẩm & Kho** | Tạo/sửa/ngừng bán sản phẩm; xem tồn kho (available/reserved) và điều chỉnh ±. |
| **Đơn hàng & Thanh toán** | Tạo đơn nhiều dòng, lọc theo user/trạng thái, xem chi tiết, hủy/hoàn tất; tạo payment với Idempotency-Key (có nút gửi lại key để thấy `200` replay), submit, callback thành công/thất bại, hủy payment. |

Panel **Nhật ký request** bên phải ghi mọi request (method, đường dẫn, mã HTTP, thời gian); bấm vào một dòng để xem header, body gửi đi và phản hồi.

## Lưu ý

- Dữ liệu do kịch bản tạo ra (user `fe-…@test.invalid`, sản phẩm `FE-…`) **không bị xóa**. Muốn sạch, reset DB (`docker compose down -v`).
- Một số lỗi trong kịch bản là cố ý (4xx); trình duyệt sẽ in chúng ở Console, không phải lỗi của ứng dụng.
- Giao diện chỉ là công cụ thử, không phải frontend sản phẩm: chưa có xác thực, chưa phân quyền.
