# AstraXRace – Hệ thống Chủng tộc cho BoxMine RPG

Plugin **Paper 1.21.x** · **Java 21** · Maven · layout phẳng (mọi file ở thư mục gốc, 1 package `me.astrax.race`) để upload thẳng lên GitHub.

## Tính năng

- 9 chủng tộc mặc định: **Human, Elf, Dwarf, Orc, Demon, Angel, Dragon, Fish (Cá), Snake (Rắn)** – mỗi race có máu, tốc độ, sức mạnh, chí mạng, nội tại, điểm mạnh/yếu và 2–3 kỹ năng riêng.
- **Cosmetic nhận dạng**: mỗi race có cánh / vật phẩm / đuôi đi theo sau lưng người chơi (cánh rồng Ender vỗ cánh, đàn cá bay theo, đuôi rắn uốn lượn, cánh thiên thần + hào quang…), cấu hình hoàn toàn trong YAML.
- **GUI `/race`**: danh sách race → click xem chi tiết (tên, mô tả, chỉ số, skill, điểm mạnh, điểm yếu) → nút **Xác nhận**. Số lần đổi race cấu hình trong `config.yml` (`race.max-changes`).
- **Kỹ năng**: chuột phải / Shift + chuột phải với **Gậy Chủng Tộc**, hoặc `/race skill <id>`. Có cooldown, năng lượng, particle, âm thanh, animation (RING / SPIRAL / BURST), sát thương/hiệu ứng – tất cả trong YAML.
- **Cấp độ**: Race Level, Race EXP, Skill Points. Lên cấp → tăng chỉ số, mở skill mới, nhận điểm nâng cấp.
- **GUI `/race upgrade`**: Strength, Defense, Speed, Health, Critical, Skill Power (giới hạn cấp, giá, giá trị mỗi cấp cấu hình được).
- Lưu **SQLite** (mặc định, `players.db`) hoặc **YAML** (`players.yml`); ghi bất đồng bộ, reconnect giữ nguyên race/level, `/race reload` không mất dữ liệu.
- **Vé Roll Tộc**: quay ngẫu nhiên chủng tộc theo độ hiếm (Thường → Huyền thoại), có hoạt ảnh vòng quay, bảo hiểm (pity) và xác nhận khi đã có tộc.
- **Hồi chiêu rõ ràng**: mọi skill đều có cooldown; ActionBar có thanh tiến độ, BossBar tiến độ, HUD trạng thái từng skill khi cầm Gậy Chủng Tộc, thông báo khi hồi xong.
- **Không có skill lướt đi**: các tộc chỉ có skill sát thương diện rộng / buff / hồi máu / chống rơi.
- Hỗ trợ **PlaceholderAPI** (tùy chọn). Không bắt buộc dependency nào ngoài Paper.

## Cấu trúc project (flat)

```
AstraXRace-flat/
├─ pom.xml                 # Maven (sourceDirectory = thư mục gốc)
├─ plugin.yml  config.yml  races.yml  messages.yml
├─ AstraXRace.java         # Main
├─ ConfigManager.java      # nạp YAML, tin nhắn, tạo item
├─ StorageManager.java     # SQLite / YAML
├─ PlayerRaceData.java     # dữ liệu người chơi
├─ Race.java               # định nghĩa race (đọc từ races.yml)
├─ RaceManager.java        # race, level, nâng cấp, attribute, công thức sát thương
├─ SkillManager.java       # tạo skill, cooldown, năng lượng, vật phẩm skill
├─ Skill.java              # lớp cơ sở
├─ AoeDamageSkill.java  SelfBuffSkill.java  HealSkill.java  NoFallSkill.java
├─ SkillEffects.java       # particle / sound / animation
├─ EffectSpec.java         # hiệu ứng potion cấu hình bằng YAML
├─ GUIManager.java  RaceGuiHolder.java
├─ CosmeticDef.java  CosmeticManager.java   # cánh/vật phẩm đi theo (ItemDisplay)
├─ RollManager.java        # Vé Roll Tộc: độ hiếm, pity, hoạt ảnh quay
├─ CooldownManager.java    # hồi chiêu: ActionBar/BossBar/thông báo sẵn sàng
├─ RacePlaceholders.java   # PlaceholderAPI (%astraxrace_...%)
├─ CommandManager.java     # lệnh + tab completion
└─ RaceListener.java       # sự kiện
```

## Build ra file .jar

Yêu cầu: **JDK 21** và **Maven 3.8+**.

```bash
mvn clean package
```

File jar: `target/AstraXRace-1.0.0.jar`. Dependency duy nhất là `paper-api` (scope `provided`, Maven tự tải từ repo PaperMC).

## Cài đặt

1. Dùng server **Paper 1.21.x** chạy Java 21.
2. Bỏ `AstraXRace-1.0.0.jar` vào thư mục `plugins/` và khởi động lại server.
3. Plugin tạo `config.yml`, `races.yml`, `messages.yml` và `players.db` trong `plugins/AstraXRace/`.
4. Chỉnh YAML xong dùng `/race reload` (đổi `storage.type` cần restart).

> Hỗ trợ cả 1.21.0–1.21.2 lẫn 1.21.3+ (plugin tự dò tên attribute theo cả 2 kiểu key).

## Lệnh & quyền

| Lệnh | Mô tả | Quyền |
|---|---|---|
| `/race` | Mở GUI chọn race | `astraxrace.use` |
| `/race choose <race>` | Chọn race | `astraxrace.use` |
| `/race info` | Thông tin race/level | `astraxrace.use` |
| `/race skill [id]` | Danh sách skill / dùng skill | `astraxrace.use` |
| `/race upgrade` | GUI nâng cấp | `astraxrace.use` |
| `/race item` | Nhận Gậy Chủng Tộc | `astraxrace.use` |
| `/race level [người]` | Xem cấp (xem người khác cần admin) | `astraxrace.use` / `astraxrace.admin` |
| `/race reload` | Nạp lại cấu hình | `astraxrace.admin` |
| `/race set <người> <race>` | Đặt race (không tính lượt đổi) | `astraxrace.admin` |
| `/race reset <người>` | Xóa race và tiến trình | `astraxrace.admin` |
| `/race giveexp <người> <số>` | Cộng Race EXP | `astraxrace.admin` |
| `/race roll` | Dùng Vé Roll Tộc (hoặc chuột phải vé) | `astraxrace.use` |
| `/race pity` | Xem số vé và bộ đếm bảo hiểm | `astraxrace.use` |
| `/race ticket give <người> [số]` | Phát Vé Roll Tộc | `astraxrace.admin` |

Quyền: `astraxrace.use` (mặc định true), `astraxrace.admin` (op), `astraxrace.bypass` (op – bỏ cooldown/năng lượng skill và giới hạn đổi race). Alias lệnh: `/chungtoc`.

## Tùy chỉnh `races.yml`

```yaml
races:
  elf:
    display-name: '&a&lElf'
    stats: {health: 16.0, speed: 1.10, strength: 1.0, jump: 0.2, knockback-resistance: 0.0, critical-chance: 0.05}
    growth: {health: 0.15, speed: 0.0005, strength: 0.004}   # cộng thêm mỗi cấp
    passives:
      bow-damage-multiplier: 1.20
```

**Nội tại (`passives`) hỗ trợ:**

| Khóa | Ý nghĩa |
|---|---|
| `exp-bonus` / `vanilla-exp-bonus` | % EXP thêm (Race EXP / EXP vanilla) |
| `melee-damage-multiplier` | nhân sát thương cận chiến |
| `bow-damage-multiplier` | nhân sát thương cung/nỏ (mũi tên) |
| `underground: {max-y, damage-multiplier}` | tăng sát thương khi Y ≤ `max-y` |
| `low-health: {threshold, damage-multiplier}` | tăng sát thương khi máu ≤ ngưỡng (tỉ lệ 0..1) |
| `on-hit` | danh sách hiệu ứng khi đánh trúng (`type`, `duration`, `amplifier`, `chance`; `type: FIRE` = đốt) |
| `damage-taken` | hệ số sát thương nhận theo `DamageCause` (`FIRE: 0.0` = miễn nhiễm) |
| `immune-effects` | danh sách hiệu ứng potion bị chặn |
| `damage-tags` / `weak-to` | thẻ sát thương gây ra (vd `HOLY`) / hệ số sát thương nhận từ thẻ đó |
| `permanent-effects` | hiệu ứng potion thường trực |

**Loại skill:** `AOE_DAMAGE`, `SELF_BUFF`, `HEAL`, `NO_FALL` – tham số trong `params`, hiệu ứng hình ảnh trong `effects` (`sound`, `particle`, `animation`). Trigger: `RIGHT_CLICK`, `SHIFT_RIGHT_CLICK`, `COMMAND`.

Tên particle theo enum `Particle` của Bukkit (FLAME, HEART, END_ROD…); âm thanh dùng khoá Minecraft (`entity.blaze.shoot`).

## Vé Roll Tộc

- Phát vé: `/race ticket give <người> [số lượng]`. Người chơi **chuột phải vé** (hoặc `/race roll`) để quay.
- Mỗi tộc có `rarity` trong `races.yml`; tỉ lệ từng độ hiếm nằm ở `roll.rarities` trong `config.yml` (mặc định Thường 50% · Hiếm 30% · Sử thi 15% · Huyền thoại 5%). Khi quay: chọn độ hiếm theo trọng số rồi chọn ngẫu nhiên một tộc cùng độ hiếm.
- **Bảo hiểm (pity)**: sau `roll.pity.epic` lượt không ra Sử thi trở lên thì lượt kế tiếp chắc chắn ra Sử thi+; sau `roll.pity.legendary` lượt không ra Huyền thoại thì chắc chắn ra Huyền thoại. Bộ đếm lưu theo từng người chơi (SQLite/YAML).
- Đã có tộc: vé thay thế tộc hiện tại nên cần **dùng vé lần 2 trong `confirm-seconds` giây** để xác nhận (`roll.confirm-when-has-race`). Roll **không tính vào số lần đổi tộc**, nhưng vẫn reset level/nâng cấp theo `race.reset-progress-on-change`.
- Kết quả được quyết định và áp dụng ngay khi dùng vé (không thể thoát game để né); toàn bộ hoạt ảnh chỉ để trình diễn. Tộc Huyền thoại được thông báo toàn server.
- **Hiệu ứng lúc quay:**
  - *Đang quay:* GUI vòng quay chạy chậm dần, viền đổi màu cầu vồng, ActionBar chạy tên tộc, âm thanh nốt nhạc tăng dần. Quanh người chơi hiện **vòng ma pháp** (2 vòng quay ngược chiều + ngôi sao 6 cánh) xoay nhanh dần và co lại, xoắn ốc bay lên, hạt sáng bay hút vào người, tiếng năng lượng dâng cao.
  - *Hồi hộp:* vòng quay dừng, **nhịp tim dồn dập** và vòng ma pháp đập theo nhịp trước khi lộ kết quả.
  - *Lộ kết quả:* viền GUI đổi sang màu độ hiếm, tiêu đề + âm thanh riêng; **cầu nổ, sóng xung kích, cột sáng** theo màu độ hiếm; pháo hoa bằng particle (an toàn, không gây sát thương); độ hiếm cao nhất có thêm **sét hiệu ứng** (không gây sát thương/cháy).
  - Chỉnh màu viền, màu hạt, số đợt pháo hoa, sét ở `roll.rarities`; bật/tắt, mật độ, thời gian hồi hộp ở `roll.effects` (giảm `density` nếu server yếu).
- Tùy chỉnh vé ở mục `ticket` (vật phẩm, tên, lore).

## Hồi chiêu & HUD

- Mọi skill đều có `cooldown` riêng (giây) và `energy-cost`; quyền `astraxrace.bypass` bỏ qua cả hai.
- Thử dùng khi đang hồi: ActionBar `⏳ Tên skill 8.2s [████░░░░]` + âm thanh nhắc.
- **BossBar** tiến độ hồi chiêu của skill vừa dùng; **HUD** trên ActionBar khi cầm Gậy Chủng Tộc (✔ sẵn sàng / số giây hồi / khóa theo cấp) kèm năng lượng; thông báo + âm thanh khi hồi xong. Chỉnh trong mục `cooldown` của `config.yml` và các khóa `hud-*` trong `messages.yml`.
- Cooldown được giữ khi thoát/vào lại server.

## PlaceholderAPI (tùy chọn)

`%astraxrace_race%` · `_race_id` · `_rarity` · `_level` · `_exp` · `_required` · `_points` · `_tickets` · `_changes_left` · `_pity_epic` · `_pity_legend`

## Cosmetic (cánh, vật phẩm đi theo, đuôi)

Mỗi race có mục `cosmetic` trong `races.yml`. Cosmetic là các entity **ItemDisplay** bám theo người chơi (không lưu xuống đĩa, tự dọn khi thoát/đổi race/tắt plugin/chết, ẩn khi tàng hình, spectator hoặc đang lướt Elytra).

```yaml
cosmetic:
  parts:
    wings:
      type: FIXED            # FIXED = gắn sau lưng | FOLLOWER = bay lơ lửng đi theo | TAIL = chuỗi đốt (đuôi rắn)
      item: ELYTRA
      mirror: true           # tạo thêm bản đối xứng (cánh trái/phải)
      scale: 2.0
      side: 0.1  up: 1.25  back: 0.2     # vị trí so với người chơi (bên cạnh / chiều cao / lùi sau)
      tilt: 20  spread: 30  pivot: 1.0   # nghiêng, góc mở cánh, xoay quanh mép trong
      flap: {amplitude: 22, speed: 0.22, air-multiplier: 2.0}   # vỗ cánh (nhanh hơn khi ở trên không)
  particles:
    ender: {type: PORTAL, count: 6, spread: 0.5, speed: 0.4, interval: 6, at: BACK}
```

| Khóa | Áp dụng | Ý nghĩa |
|---|---|---|
| `type` | tất cả | `FIXED`, `FOLLOWER`, `TAIL` |
| `item`, `item-model`, `custom-model-data` | tất cả | Vật phẩm hiển thị; 2 khóa sau dùng cho resource pack (xem dưới) |
| `scale`, `end-scale` | tất cả | Kích thước (`end-scale` = đốt cuối của TAIL) |
| `side`, `up`, `back` | tất cả | Vị trí tương đối. `back` = khoảng cách lùi ra sau lưng (mặc định đã đặt 0.75–0.9 để vật phẩm cách xa người chơi, không chạm vào lưng) |
| `tilt`, `spread`, `pitch`, `pivot`, `mirror`, `flap` | FIXED | Dáng cánh/vật phẩm gắn lưng |
| `follow-speed`, `bob: {amplitude, speed}` | FOLLOWER | Độ "trễ" khi bay theo và nhấp nhô |
| `segments`, `spacing`, `wiggle`, `end-item` | TAIL | Số đốt, khoảng cách đốt, độ uốn lượn, vật phẩm đốt cuối |
| `billboard` | tất cả | `FIXED`, `CENTER`, `VERTICAL`, `HORIZONTAL` |
| `owner-visible` | tất cả | `false` = chính chủ không nhìn thấy (tránh vướng tầm nhìn) |

Particle `at`: `BACK` (sau lưng) hoặc `TAIL_END` (đầu mút đuôi); `moving-only: true` = chỉ khi đang di chuyển.

Bật/tắt toàn cục và chỉnh hiệu năng ở mục `cosmetic` trong `config.yml` (`enabled`, `update-ticks`, `view-range`…).

**Về hình dạng cánh:** mặc định cosmetic dùng icon vật phẩm 2D của Minecraft (ví dụ Elytra, Feather), nên cánh là hình phẳng được vỗ bằng animation. Muốn cánh 3D "thật", dùng resource pack và gán `item-model: 'tên_pack:ten_model'` (1.21.2+) hoặc `custom-model-data: <số>` cho part tương ứng.

## Skill: tham số & hiệu ứng nâng cao

**`AOE_DAMAGE`**: `radius`, `damage` (**mỗi đợt**), `waves` (số đợt), `wave-interval` (tick giữa các đợt), `follow` (đợt sau bám theo người dùng), `fire-ticks`, `knockback`, `lift` (hất lên), `pull` (kéo vào tâm thay vì đẩy ra), `lightning` (hiệu ứng sét tại mục tiêu, không gây sát thương sét), `effects`.

**`HEAL`**: `amount`, `radius`, `cleanse`, `effects` (buff áp lên mọi người được hồi). **`NO_FALL`**: `duration-ticks`, `slow-falling`, `effects`. **`SELF_BUFF`**: `effects`, `heal`.

**Hiệu ứng hình ảnh (`effects`)** – xếp nhiều lớp chồng lên nhau:

```yaml
effects:
  sounds:                                   # nhiều âm thanh, có thể trễ (delay, tick)
    cast:  {key: entity.blaze.shoot, volume: 1.0, pitch: 0.7}
    boom:  {key: entity.generic.explode, volume: 0.8, pitch: 1.2, delay: 4}
  particle: {type: FLAME, count: 80, spread: 1.6, speed: 0.1}     # nổ nhỏ lúc tung skill
  animations:                               # nhiều lớp animation chạy song song
    wave:   {type: SHOCKWAVE, particle: FLAME, radius: 7.5, duration: 14, count: 40}
    vortex: {type: VORTEX, particle: SOUL_FIRE_FLAME, radius: 3.0, height: 3.2, duration: 18, delay: 4}
  impact:  {particle: {type: FLAME, count: 14, spread: 0.4, speed: 0.06}, sound: {key: entity.blaze.hurt}}
```

| Loại animation | Hình dạng |
|---|---|
| `RING` | vòng tròn mở rộng dưới chân |
| `SHOCKWAVE` | 2 vòng sóng xung kích mở rộng nhanh |
| `SPIRAL` | xoắn ốc bay lên quanh người |
| `VORTEX` | lốc xoáy vừa nở rộng vừa bay lên |
| `PILLAR` | cột sáng dâng lên |
| `BURST` | cầu nổ mở rộng |
| `DOME` | mái vòm nửa cầu mở rộng |
| `TRAIL` | vệt hạt bám theo người dùng |

Mỗi lớp có: `particle`, `radius`, `height`, `duration` (tick), `count` (số điểm), `delay` (tick trước khi bắt đầu).

## Khắc chế giữa các tộc mới

| Tộc | Sát thương gây ra có thẻ | Nhận thêm sát thương từ |
|---|---|---|
| Dragon | `FIRE` | `WATER` (Fish) +25% |
| Fish | `WATER` | `VENOM` (Snake) +25% |
| Snake | `VENOM` | `FIRE` (Dragon) +25% |

Fish còn có nội tại dưới nước (`water-effects`, `water-damage-multiplier`). Hệ số chỉnh trong `weak-to` của `races.yml`.

## Thêm loại skill mới

1. Tạo class kế thừa `Skill`, cài `execute(...)`.
2. Đăng ký trong `SkillManager#buildSkill` (thêm 1 dòng `case`).
3. Dùng `type: TÊN_MỚI` trong `races.yml`.

## Ghi chú vận hành

- Modifier attribute của plugin được gỡ khi người chơi thoát và khi tắt plugin → không để lại chỉ số "kẹt" nếu gỡ plugin.
- Nếu tải dữ liệu từ DB lỗi, plugin không ghi đè bản ghi của người chơi đó (tránh mất dữ liệu).
- Trạng thái tạm (cooldown, năng lượng, chống rơi) được dọn khi người chơi thoát.
- Sát thương skill bỏ qua bộ xử lý sát thương thường để không bị nhân đôi hệ số.
