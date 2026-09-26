# PIHAUPA Java - bám sát bài báo

Chương trình cài đặt thuật toán **PIHAUPA** trong bài báo *Intelligent average utility pattern analysis using pre-large concept in dynamic stream data* (Kim et al., Journal of Big Data, 2026).

## Phạm vi cài đặt

- Đọc dữ liệu giao dịch định lượng từ file văn bản.
- Tính `U(i,T)`, `U(P,T)`, `TU(T)`, `MU(T)`, `AU(P)`, `AUUB`, `MRU`, `MRN` và cận trên `MAU` theo bài báo.
- Xếp mẫu thành `LARGE`, `PRE-LARGE`, `SMALL` bằng hai ngưỡng `Su` và `Sl`.
- Khởi tạo PIHAUP-List, sắp theo AUUB tăng dần, mở rộng mẫu DFS và cắt tỉa bằng MAU.
- Khi thêm batch mới, kiểm tra tight re-scan Eq. (6). Nếu không re-scan, chỉ cập nhật các mẫu LARGE/PRE-LARGE đang giữ trong pattern tree.
- In cả điều kiện tight Eq. (6) và original Eq. (5) để đối chiếu.
- Chạy hai chính sách Original/Tight trên cùng dữ liệu và so sánh số re-scan, thời gian,
  heap quan sát, pattern đã duyệt, node đã kết hợp và độ tương đương HAUP.
- Xuất input chuẩn hóa, JSON và CSV khi dùng `--output-dir`.

## Công thức

Với `u(i,T) = IU(i,T) * EU(i)`:

```text
TU(T)   = sum u(i,T)
MU(T)   = max u(i,T)
U(P,T)  = sum u(i,T), i thuộc P và P thuộc T
AU(P,T) = U(P,T) / |P|
AU(P)   = sum AU(P,T) trên các T chứa P

LARGE:     AU(P) >= Su * TU(DB)
PRE-LARGE: Sl * TU(DB) <= AU(P) < Su * TU(DB)
SMALL:     AU(P) < Sl * TU(DB)
```

Tight re-scan Eq. (6):

```text
MU(ID) >= ((Su - Sl) / (1 - TU(ID)/MU(ID) * Su)) * TU(OD)
```

Chương trình so sánh bằng dạng ổn định đại số của bài báo (Eq. 12), tránh phép chia:

```text
MU(ID) - Su * TU(ID) >= (Su - Sl) * TU(OD)
```

`OD` là dữ liệu ở lần full scan gần nhất; `ID` là toàn bộ dữ liệu được thêm sau lần đó. Khi bằng ngưỡng (`>=`) vẫn re-scan.

## Chạy

Yêu cầu JDK 17 trở lên.

Mở ứng dụng Desktop (không đóng gói EXE):

```powershell
.\run-desktop.ps1
```

Desktop tự chạy dữ liệu Tables 2–3 khi mở để hiển thị một báo cáo mẫu. Sau đó có thể
chọn file khác, chỉnh `Su`/`Sl` và bấm **Chạy và so sánh**. Báo cáo cuộn dọc gồm
kết quả chính, dữ liệu đã đọc, công thức và ngưỡng, diễn tiến từng batch, so sánh
Original/Tight, rồi bảng mẫu cuối với phép đối chiếu AU và ngưỡng.

Mở giao diện Web local (server tự mở trình duyệt):

```powershell
.\run-web.ps1
```

Hai giao diện đều phân tách kết quả theo batch, LARGE/HAUP, PRE-LARGE, quyết định
re-scan và tài nguyên. Thời gian là thời gian thực đo trong JVM; với dữ liệu nhỏ,
chương trình warm-up cả hai chế độ và dùng trung vị ba lượt xen kẽ để giảm sai lệch.

Chạy dữ liệu mặc định từ Tables 2-3 của bài báo (`Su=0.23`, `Sl=0.10`):

```powershell
.\run.ps1
```

Đọc file và dùng ngưỡng trong file:

```powershell
.\run.ps1 --input examples\paper-example.txt
```

Ghi đè ngưỡng thủ công và xuất báo cáo:

```powershell
.\run.ps1 --input examples\paper-example.txt --su 0.23 --sl 0.10 --output-dir results\paper
```

Xem trợ giúp:

```powershell
.\run.ps1 --help
```

## Định dạng input

```text
upper=0.23
lower=0.10

external:
A=2 B=5 C=1 D=3 E=6 F=4

batch DB0
T1: A:2 B:1 C:3
T2: C:1 D:2

batch DB1
T3: A:3 F:3
```

Giá trị sau item là **internal utility**; phần `external` là **external utility**. Tên transaction phải duy nhất trên mọi batch, mỗi transaction không được lặp item, và mọi utility phải dương.

## Kết quả chuẩn của ví dụ bài báo

| Batch | TU batch | MU batch | TU tích luỹ | Tight re-scan | LARGE/HAUP |
|---|---:|---:|---:|---|---|
| DB0 | 141 | 73 | 141 | khởi tạo | E=60, BE=45, B=35, AE=34 |
| DB1 | 32 | 24 | 173 | không (`24 < 26.438`) | E=72, BE=45 |
| DB2 | 67 | 40 | 240 | có (ID tích luỹ DB1+DB2) | E=84, B=75, BE=63.5 |

Sau DB1, `AE=34` và `B=35` chuyển thành PRE-LARGE; `CAE=15.333` thành SMALL và bị loại khỏi pattern tree. DB2 làm điều kiện tight đạt, nên chương trình full scan và mở rộng mẫu lại.

## Kiểm thử

```powershell
.\run-tests.ps1
```

Bộ test kiểm tra số liệu Tables 2-5, ranh giới phân loại, dấu `>=` của re-scan, tích luỹ ID qua nhiều batch, đối chiếu kết quả bằng vét cạn trên dữ liệu ngẫu nhiên nhỏ và dữ liệu input không hợp lệ.

### Bộ dữ liệu lớn có đáp án đối chiếu

[`examples/paper-scaled-9000.txt`](examples/paper-scaled-9000.txt) có 9.000 giao dịch:
mỗi giao dịch trong Tables 2–3 được lặp 1.000 lần với TID duy nhất. File giữ nguyên
`Su=0.23`, `Sl=0.10`. Vì vậy utility và các ngưỡng phải gấp đúng 1.000 lần ví dụ
bài báo; cấu trúc mẫu và quyết định re-scan phải giữ nguyên.

| Batch | Số giao dịch tích luỹ | TU tích luỹ | Upper | Lower | Số LARGE |
|---|---:|---:|---:|---:|---:|
| DB0 | 5.000 | 141.000 | 32.430 | 14.100 | 4 |
| DB1 | 7.000 | 173.000 | 39.790 | 17.300 | 2 |
| DB2 | 9.000 | 240.000 | 55.200 | 24.000 | 3 |

AU cuối: `E=84.000`, `B=75.000`, `BE=63.500`. Tight re-scan một lần ở
DB2; Original re-scan ở DB1 và DB2. Chạy bộ kiểm chứng độc lập theo định nghĩa
`AU(P)` (vét cạn mọi tập con của A–F):

```powershell
.\verify-large.ps1
```

Tạo lại file với hệ số lặp khác, ví dụ 2.000 lần (18.000 giao dịch):

```powershell
.\generate-paper-scaled.ps1 2000
.\verify-large.ps1 examples\paper-scaled-18000.txt
```

Desktop mở sẵn 25 giao dịch đầu khi đọc file lớn và có nút xem thêm. Thuật toán
vẫn tính trên toàn bộ file, không chỉ phần đang hiển thị.

Khi xuất kết quả, `comparison.csv` và `comparison.json` chứa bảng so sánh Original
Eq.(5) với Tight Eq.(6); các file còn lại chứa pattern và hiệu năng chi tiết từng batch.
