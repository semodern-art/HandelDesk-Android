# Архитектура Android HandelDesk

## Общая схема

OpenCart ⇄ HandelDesk Bridge 0.4.1 ⇄ Android / Windows

Android и Windows — равноправные клиенты. OpenCart остаётся сервером публикации, а вся ежедневная работа выполняется локально.

## Android слои

- `core/` — platform-independent правила: поиск, SEO, donor replacement, three-way merge primitives.
- `data/local/` — Room entities/DAO.
- `data/remote/` — HTTPS Bridge client.
- `data/repo/` — Snapshot, Editor, References, Sync, Image Cache.
- `domain/` — OpenCart payload helpers, validation.
- `ui/` — Jetpack Compose.

## Безопасность данных

1. Серверные snapshot-поля хранятся отдельно как `baseJson`.
2. Текущая offline-версия — `localJson`.
3. Изменения существующих товаров записываются field-level в `changes`.
4. Новый товар имеет `__create__`; при Sync отправляется его текущий полный `local`.
5. Quantity `+N/-N` записывается как `adjust`, а не как опасный Set.
6. Snapshot не перезаписывает локально изменённые сущности.
7. Sync всегда делает свежий Bridge Preview.
8. Apply выполняется только вручную.
9. Temporary IDs новых категорий/производителей после Sync remap-ятся только в известных relationship-полях.
10. Device Token хранится зашифрованным Android Keystore; HTTP и redirects запрещены.

## Offline images

Существующие картинки кешируются как файлы в private app storage. Имя файла является SHA-256 от серверного пути. Room не раздувается бинарными данными.
