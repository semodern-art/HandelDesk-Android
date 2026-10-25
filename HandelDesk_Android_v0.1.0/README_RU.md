# HandelDesk Android v0.1.0-alpha

Нативный Android-клиент HandelDesk для offline-first работы с каталогом OpenCart.

## Главный принцип

Android НЕ является отдельной системой. Он использует тот же OpenCart Bridge protocol 0.4.1 и те же соглашения, что desktop HandelDesk:

- `server-product-{id}` / `server-category-{id}` / `server-manufacturer-{id}`;
- BASE / LOCAL / SERVER;
- field-level ChangeSet;
- `adjust` для относительного изменения остатка;
- строгий SEO policy `handeltheke-product-v1`;
- серверный Preview перед Apply;
- READ ONLY / SAFE TEST / LIVE контролируется самим Bridge.

## Уже реализовано в alpha

- Kotlin + Jetpack Compose;
- Room/SQLite, WAL, автосохранение локально;
- защищённое хранение Device Token через Android Keystore;
- HTTPS-only Bridge client, redirects запрещены;
- Snapshot всего каталога и справочников;
- локальный поиск с `"фраза"`, `-слово`, `-"фраза"`, `OR`, `model:`, `sku:`, `jan:`, `ean:`, `mpn:`, `id:`;
- список товаров карточками и пагинация;
- быстрый редактор Model / Price / Quantity / Status / Manufacturer / Categories;
- полная карточка в OpenCart-порядке;
- создание пустого товара;
- создание из донора с выбором переносимых секций;
- Quick Replace старой модели на новую;
- donor checklist;
- строгая SEO-нормализация Handeltheke;
- создание новой категории и производителя прямо из товара;
- Options: просмотр и изменение количества значений;
- локальная заметка и рабочий статус READY;
- Work Queue;
- ChangeSet с включением/исключением изменений;
- локальная Validation перед Sync;
- Sync Preview / ручной APPLY через существующий Bridge;
- remap временных отрицательных category/manufacturer ID после Sync;
- Audit Trail;
- Prepare Offline;
- кеш главных или всех существующих изображений для работы в авиарежиме.

## Намеренно отсутствует

Никакого barcode scanner, OCR или распознавания товара по наклейке.

## Пока не завершено в alpha

- добавление новых фотографий с телефона + upload queue;
- полный Add/Edit UI Options/Attributes (структура данных уже поддерживается);
- Specials/Discounts/Related UI;
- configurable Validation Rules как в desktop v0.5;
- Undo/Redo;
- HDO v2 import/export;
- backup/recovery UI;
- полноценный conflict-resolution UI (preview конфликты показывает и Apply блокирует);
- Android ↔ PC direct pairing.

Эти блоки входят в следующие Android-релизы и не требуют отдельного backend.

## Подключение

1. На OpenCart должен быть установлен `bridge/OpenCart_Bridge_v0.4.1.ocmod.zip`.
2. Для первого подключения оставить Bridge в `READ ONLY`.
3. В Android открыть `Ещё → Настройки`.
4. Ввести Bridge URL и Device Token.
5. Проверить соединение.
6. `Ещё → Подготовить Offline` → выбрать кеш фото → `ПОДГОТОВИТЬ OFFLINE`.

После `OFFLINE READY` каталог и выбранные изображения доступны без сети.

## Сборка

Проект рассчитан на Android Studio / JDK 17. В текущей build-среде Android SDK отсутствует, поэтому APK в этот alpha-архив не включён. Pure Kotlin `core` протестирован отдельно; см. `docs/TEST_REPORT_RU.md`.
