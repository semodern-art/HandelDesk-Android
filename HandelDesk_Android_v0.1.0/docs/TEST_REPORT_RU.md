# Test report — HandelDesk Android v0.1.0-alpha

## Выполнено

- Pure Kotlin core скомпилирован `kotlinc` и запущен smoke test: PASS.
- Search parser: точная фраза / исключения / OR / field prefixes: PASS.
- Handeltheke SEO policy: PASS.
- Donor token replacement: PASS.
- Three-way merge primitives: PASS.
- Проведена статическая ревизия Room entity/DAO связей, Bridge payload shape и protocol 0.4.1.
- Проверено, что Android использует те же deterministic server UUID, что desktop.
- Проверена серверная форма `sync_preview`, `sync_apply`, `id_map`, `reference_results`, product results по исходнику Bridge v0.4.1.
- Добавлен безопасный ID remap для temporary category/manufacturer IDs.
- Проверено отсутствие barcode/OCR/scanner зависимостей и permission.

## Ограничение build-среды

В контейнере отсутствуют Android SDK/Gradle artifacts и нет сетевого доступа к Maven/Google repositories. Поэтому полноценный `assembleDebug`, Android Lint и instrumented tests здесь не выполнены. Это означает, что v0.1.0 является SOURCE ALPHA, а не install-ready APK.

Перед первым APK необходимо:
1. открыть проект в актуальном Android Studio с JDK 17;
2. Gradle Sync;
3. `assembleDebug`;
4. Android Lint;
5. Room schema generation;
6. smoke test на реальном Android устройстве в READ ONLY.

## Последний локальный прогон

- `kotlinc` core smoke: OK
- parser-level check всех Android Kotlin source: parser errors 0 (Android/Compose symbols ожидаемо unresolved без SDK)
- required `rememberSaveable` imports: OK
- scanner/OCR/barcode dependencies/permissions: absent
- Bridge ZIP integrity: OK
