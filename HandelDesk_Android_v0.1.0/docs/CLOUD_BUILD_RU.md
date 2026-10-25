# Облачная сборка APK

Проект содержит `.github/workflows/build-apk.yml`.

Workflow автоматически:
- использует JDK 17;
- устанавливает Gradle 9.6.0;
- устанавливает Android SDK Platform 37 и Build Tools 36.0.0;
- выполняет `:app:assembleDebug`;
- проверяет наличие APK;
- публикует `app-debug.apk` как GitHub Actions artifact.

Итоговый APK подписан стандартным debug-ключом Android и предназначен для прямой тестовой установки на телефон. Для production-релиза нужен отдельный release keystore.
