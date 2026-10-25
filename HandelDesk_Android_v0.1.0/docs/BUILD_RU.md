# Сборка Android alpha

## Что требуется

- Android Studio Rabbit 1 / совместимая актуальная версия;
- JDK 17;
- Android SDK Platform 37;
- интернет во время первого Gradle Sync для загрузки зависимостей.

## Открытие

1. Распаковать `HandelDesk_Android_v0.1.0-alpha.zip`.
2. Android Studio → Open → выбрать папку `HandelDesk_Android_v0.1.0`.
3. Дождаться Gradle Sync.
4. Если IDE сообщает об отсутствии Gradle wrapper JAR, выбрать использование установленного Gradle 9.6 либо выполнить `gradle wrapper --gradle-version 9.6.0` один раз.
5. Build → Make Project.
6. Для debug APK: Build → Build APK(s).

## Первый запуск

До любых записей Bridge оставить в READ ONLY.

В приложении:
- Ещё → Настройки;
- Bridge URL;
- Device Token;
- Проверить;
- Подготовить Offline.

Первый тест записи выполнять только после проверки Snapshot и только через SAFE TEST на Bridge.
