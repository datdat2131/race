# AstraXRace – Hệ thống Chủng tộc (chỉ số + nội tại + buff)

Plugin Paper **1.21.x** · Java **21** · Maven · layout phẳng (mọi file ở thư mục gốc, 1 package `me.astrax.race`) để upload thẳng lên GitHub.

> **Phiên bản này không còn skill chủ động** (không cooldown, không năng lượng, không Gậy Chủng Tộc). Mọi sức mạnh của tộc là **buff tự động**: chỉ số nền + nội tại + buff theo điều kiện, mở khóa dần theo cấp.

## Tính năng

- **9 chủng tộc:** Human, Elf, Dwarf, Orc, Demon, Angel, Dragon, Fish (Cá), Snake (Rắn) – mỗi tộc có chỉ số, nội tại, 3–4 buff, điểm mạnh/yếu, độ hiếm.
- **Buff tự động theo điều kiện**: dưới nước, dưới lòng đất, ban ngày/đêm, trong Nether/The End, trời mưa, máu thấp/đầy, đang cháy, ngồi xuống, chạy nước rút, độ cao lớn… Có thể tăng sát thương, giảm sát thương nhận, hồi máu, cấp hiệu ứng potion, hoặc kích hoạt khi hạ gục / khi bị đánh.
- **Cấp độ**: Race Level, EXP, điểm nâng cấp; lên cấp tăng chỉ số và **mở khóa buff mới**.
- **GUI `/race`**: danh sách tộc (hiện độ hiếm) → chi tiết (chỉ số, buff, điểm mạnh/yếu) → xác nhận. Số lần đổi tộc cấu hình được.
- **GUI `/race upgrade`**: Strength, Defense, Speed, Health, Critical, Regen.
- **Vé Roll Tộc**: quay ngẫu nhiên tộc theo độ hiếm, có bảo hiểm (pity), hoạt ảnh vòng quay hoành tráng.
- **Cosmetic nhận dạng**: cánh / vật phẩm / đuôi đi theo sau lưng (cánh rồng Ender, đàn cá, đuôi rắn…).
- Lưu **SQLite** (mặc định) hoặc **YAML**; ghi bất đồng bộ; `/race reload` không mất dữ liệu.
- **PlaceholderAPI** (tùy chọn). Dependency bắt buộc duy nhất: Paper.

## Cấu trúc project (flat)

```
AstraXRace-flat/
├─ pom.xml
├─ plugin.yml  config.yml  races.yml  messages.yml
├─ AstraXRace.java         # Main
├─ ConfigManager.java      # nạp YAML, tin nhắn, tạo item
├─ StorageManager.java     # SQLite / YAML
├─ PlayerRaceData.java     # dữ liệu người chơi
├─ Race.java               # định nghĩa tộc (đọc từ races.yml)
├─ BuffDef.java            # định nghĩa một buff
├─ BuffManager.java        # đánh giá điều kiện, áp buff mỗi giây, hệ số sát thương
├─ EffectSpec.java         # hiệu ứng potion cấu hình bằng YAML
├─ RaceManager.java        # tộc, level, nâng cấp, attribute, công thức sát thương
├─ RollManager.java        # Vé Roll Tộc: độ hiếm, pity, hoạt ảnh quay
├─ GUIManager.java  RaceGuiHolder.java
├─ CosmeticDef.java  CosmeticManager.java   # cánh/vật phẩm đi theo (ItemDisplay)
├─ CommandManager.java  RaceListener.java  RacePlaceholders.java
```

## Build ra file .jar

Yêu cầu **JDK 21** + **Maven 3.8+**:

```bash
mvn clean package
```

Jar: `target/AstraXRace-1.0.0.jar`. Dependency: `paper-api` (provided) và `placeholderapi` (provided, chỉ cần lúc build).

## Cài đặt

1. Dùng **Paper 1.21.x** chạy Java 21; bỏ jar vào `plugins/`, khởi động lại.
2. Plugin tạo `config.yml`, `races.yml`, `messages.yml` và `players.db` trong `plugins/AstraXRace/`.
3. Chỉnh YAML xong dùng `/race reload` (đổi `storage.type` cần restart).
4. Nâng cấp từ bản có skill: dữ liệu level/EXP/tộc giữ nguyên. Nâng cấp "Skill Power" cũ không còn (thay bằng "Regen"); điểm đã dùng cho Skill Power không được hoàn lại. Gậy Chủng Tộc cũ chỉ còn là vật phẩm thường.

## Lệnh & quyền

| Lệnh | Mô tả | Quyền |
|---|---|---|
| `/race` | Mở GUI chọn chủng tộc | `astraxrace.use` |
| `/race choose <race>` | Chọn chủng tộc | `astraxrace.use` |
| `/race info` | Thông tin tộc/level | `astraxrace.use` |
| `/race buffs` | Xem buff: ● đang hoạt động / ○ chưa đủ điều kiện / 🔒 chưa mở khóa | `astraxrace.use` |
| `/race upgrade` | GUI nâng cấp | `astraxrace.use` |
| `/race level [người]` | Xem cấp (xem người khác cần admin) | `astraxrace.use` / `astraxrace.admin` |
| `/race roll` · `/race pity` | Dùng Vé Roll Tộc / xem vé và bảo hiểm | `astraxrace.use` |
| `/race ticket give <người> [số]` | Phát Vé Roll Tộc | `astraxrace.admin` |
| `/race reload` | Nạp lại cấu hình | `astraxrace.admin` |
| `/race set <người> <race>` · `/race reset <người>` · `/race giveexp <người> <số>` | Quản trị | `astraxrace.admin` |

Quyền: `astraxrace.use` (mặc định true), `astraxrace.admin` (op), `astraxrace.bypass` (op – bỏ giới hạn đổi tộc và bước xác nhận roll). Alias lệnh: `/chungtoc`.

## Tùy chỉnh `races.yml`

```yaml
races:
  fish:
    display-name: '&b&lFish'
    rarity: COMMON
    stats: {health: 18.0, speed: 0.95, strength: 1.0, jump: 0.0, knockback-resistance: 0.0, critical-chance: 0.04}
    growth: {health: 0.2, speed: 0.0004, strength: 0.005}      # cộng thêm mỗi cấp tộc
    passives:
      damage-taken: {DROWNING: 0.0}
      damage-tags: [WATER]
      weak-to: {VENOM: 1.25}
    buffs:
      sea-blessing:
        name: '&bPhước lành đại dương'
        description: ['Dưới nước: thở được, bơi nhanh, +20% sát thương.']
        condition: IN_WATER
        unlock-level: 1
        effects:
          - {type: WATER_BREATHING, duration: 40, amplifier: 0}
        damage-multiplier: 1.2
        regen: 0.3
```

### Buff

| Khóa | Ý nghĩa |
|---|---|
| `condition` | Điều kiện kích hoạt (bảng dưới). Mặc định `ALWAYS` |
| `threshold` | Ngưỡng cho điều kiện: `LOW_HEALTH`/`FULL_HEALTH` = tỉ lệ máu (0.3 = 30%); `UNDERGROUND` = Y tối đa; `HIGH_ALTITUDE` = Y tối thiểu |
| `unlock-level` | Cấp tộc cần đạt để buff hoạt động (khi lên đúng cấp sẽ có thông báo mở khóa) |
| `effects` | Danh sách potion áp liên tục khi điều kiện đúng. Để `duration: 40` (tự gia hạn mỗi giây); `NIGHT_VISION` dùng `260` để không nhấp nháy |
| `damage-multiplier` | Nhân sát thương **gây ra** (1.2 = +20%) |
| `defense-multiplier` | Nhân sát thương **nhận vào** (0.9 = giảm 10%; lớn hơn 1 = điểm yếu) |
| `regen` | HP hồi mỗi giây khi buff hoạt động |
| `on-kill: {heal, effects}` | Khi hạ gục kẻ địch (mob hostile/boss/người chơi): hồi `heal` HP và nhận `effects` |
| `on-hurt: {chance, effects}` | Khi bị sát thương: xác suất `chance` nhận `effects` |

| Điều kiện | Khi nào đúng |
|---|---|
| `ALWAYS` | luôn luôn |
| `IN_WATER` / `IN_RAIN` | đang ở trong nước / đang bị mưa |
| `UNDERGROUND` | thế giới thường, Y ≤ `threshold` (mặc định 50) |
| `NIGHT` / `DAY` | ban đêm / ban ngày ngoài trời (thế giới thường) |
| `LOW_HEALTH` / `FULL_HEALTH` | máu ≤ / ≥ `threshold` (mặc định 35% / 90%) |
| `IN_NETHER` / `IN_END` | đang ở Nether / The End |
| `SNEAKING` / `SPRINTING` / `ON_FIRE` | ngồi xuống / chạy nước rút / đang cháy |
| `HIGH_ALTITUDE` | Y ≥ `threshold` (mặc định 120) |

Nhiều buff cùng hoạt động thì hệ số **nhân với nhau**, hồi máu **cộng dồn**.

### Nội tại (`passives`) – luôn bật

| Khóa | Ý nghĩa |
|---|---|
| `exp-bonus` / `vanilla-exp-bonus` | % EXP thêm (Race EXP / EXP thường) |
| `melee-damage-multiplier` / `bow-damage-multiplier` | nhân sát thương cận chiến / cung-nỏ |
| `on-hit` | hiệu ứng khi đánh trúng (`type`, `duration`, `amplifier`, `chance`; `type: FIRE` = đốt) |
| `damage-taken` | hệ số sát thương nhận theo `DamageCause` (`FIRE: 0.0` = miễn nhiễm) |
| `immune-effects` | danh sách hiệu ứng potion bị chặn |
| `damage-tags` / `weak-to` | thẻ sát thương gây ra (vd `WATER`) / hệ số sát thương nhận từ thẻ đó |

Khắc chế giữa 3 tộc mới: Dragon (`FIRE`) → Snake +25%; Snake (`VENOM`) → Fish +25%; Fish (`WATER`) → Dragon +25%.

## Vé Roll Tộc

- Phát vé: `/race ticket give <người> [số lượng]`. Người chơi **chuột phải vé** (hoặc `/race roll`) để quay.
- Mỗi tộc có `rarity`; tỉ lệ từng độ hiếm ở `roll.rarities` (mặc định Thường 50% · Hiếm 30% · Sử thi 15% · Huyền thoại 5%): chọn độ hiếm theo trọng số rồi chọn ngẫu nhiên một tộc cùng độ hiếm.
- **Bảo hiểm (pity)**: sau `roll.pity.epic` lượt không ra Sử thi trở lên → lượt sau chắc chắn Sử thi+; sau `roll.pity.legendary` lượt → chắc chắn Huyền thoại. Xem `/race pity`.
- Đã có tộc: phải **dùng vé lần 2 trong `confirm-seconds` giây** để xác nhận. Roll không tính vào số lần đổi tộc nhưng vẫn reset level/nâng cấp theo `race.reset-progress-on-change`.
- Kết quả áp dụng ngay khi dùng vé (không né được bằng cách thoát game); hoạt ảnh chỉ để trình diễn.
- **Hiệu ứng:** GUI vòng quay chạy chậm dần, viền đổi màu; vòng ma pháp 2 lớp + ngôi sao 6 cánh xoay quanh người, hạt hút vào, nhịp tim hồi hộp trước khi lộ kết quả; rồi cầu nổ, sóng xung kích, cột sáng, pháo hoa particle theo màu độ hiếm; Huyền thoại có sét hiệu ứng và thông báo toàn server. Chỉnh ở `roll.rarities` (`pane`, `rgb`, `fireworks`, `lightning`) và `roll.effects` (`enabled`, `density`, `suspense-ticks`).

## PlaceholderAPI (tùy chọn)

`%astraxrace_race%` · `_race_id` · `_rarity` · `_level` · `_exp` · `_required` · `_points` · `_tickets` · `_changes_left` · `_pity_epic` · `_pity_legend`

## Cosmetic (cánh, vật phẩm đi theo, đuôi)

Mỗi tộc có mục `cosmetic` trong `races.yml`. Cosmetic là các entity **ItemDisplay** bám theo người chơi (không lưu xuống đĩa, tự dọn khi thoát/đổi tộc/tắt plugin/chết; ẩn khi tàng hình, spectator hoặc đang lướt Elytra).

```yaml
cosmetic:
  parts:
    wings:
      type: FIXED            # FIXED = gắn sau lưng | FOLLOWER = bay lơ lửng đi theo | TAIL = chuỗi đốt (đuôi rắn)
      item: ELYTRA
      mirror: true           # tạo thêm bản đối xứng (cánh trái/phải)
      scale: 2.4
      side: 0.1  up: 1.35  back: 0.9     # vị trí so với người chơi (bên cạnh / chiều cao / lùi sau)
      tilt: 20  spread: 30  pivot: 1.0   # nghiêng, góc mở cánh, xoay quanh mép trong
      flap: {amplitude: 22, speed: 0.22, air-multiplier: 2.0}
  particles:
    ender: {type: PORTAL, count: 6, spread: 0.5, speed: 0.4, interval: 6, at: BACK}
```

| Khóa | Áp dụng | Ý nghĩa |
|---|---|---|
| `type` | tất cả | `FIXED`, `FOLLOWER`, `TAIL` |
| `span`, `fingers`, `angle`, `flap`, `bone`, `membrane`, `tip` | WINGS | **Cánh vẽ bằng particle** (xương cánh + các ngón + màng, vỗ nhẹ): `span` = sải cánh mỗi bên (block, mặc định 1.3), `fingers` = số ngón (2-6), `angle` = góc nâng cánh (độ), `bone`/`membrane`/`tip` = particle (`DUST` có `color`/`size`, hoặc `END_ROD`...) |
| `item`, `item-model`, `custom-model-data` | tất cả | Vật phẩm hiển thị; 2 khóa sau dùng cho resource pack |
| `scale`, `end-scale` | tất cả | Kích thước (`end-scale` = đốt cuối của TAIL) |
| `side`, `up`, `back` | tất cả | Vị trí tương đối; `back` = khoảng cách lùi ra sau lưng |
| `tilt`, `spread`, `pitch`, `pivot`, `mirror`, `flap` | FIXED | Dáng cánh/vật phẩm gắn lưng |
| `follow-speed`, `bob: {amplitude, speed}` | FOLLOWER | Độ "trễ" khi bay theo và nhấp nhô |
| `segments`, `spacing`, `wiggle`, `end-item` | TAIL | Số đốt, khoảng cách, độ uốn lượn, vật phẩm đốt cuối |
| `billboard`, `owner-visible` | tất cả | Hướng mặt; `false` = chính chủ không nhìn thấy |

Cánh rồng dùng `type: WINGS` (nhỏ gọn, mảnh, không bị phóng to như icon vật phẩm); muốn đổi cho tộc khác chỉ cần thay part `wings` tương tự. Bật/tắt toàn cục và chỉnh hiệu năng ở mục `cosmetic` trong `config.yml` (`wing-density` giảm mật độ hạt cánh). Mặc định dùng icon vật phẩm 2D; muốn cánh 3D dùng resource pack (`item-model` hoặc `custom-model-data`).

## Ghi chú vận hành

- Modifier attribute của plugin được gỡ khi người chơi thoát và khi tắt plugin → không để lại chỉ số "kẹt".
- Nếu tải dữ liệu từ DB lỗi, plugin không ghi đè bản ghi của người chơi đó.
- Buff được xét mỗi giây (một task duy nhất); trạng thái tạm được dọn khi người chơi thoát.
