# Как выпускается версия

Сборка и подпись идут в GitHub Actions
([build.yml](../.github/workflows/build.yml)), локально собирается только
debug для проверки кода.

## Секреты подписи

В репозитории заданы три секрета, из них Actions собирает релизный ключ:

| Секрет | Что это |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | PKCS12-хранилище `texfi-release.jks` в base64 |
| `ANDROID_STORE_PASSWORD` | пароль хранилища |
| `ANDROID_KEY_PASSWORD` | пароль ключа (алиас `texfi`) |

Ключ тот же, что у остальных приложений TexFi. В репозитории его нет и
быть не должно: сборка без секретов не падает, но подписывается
debug-ключом, и такой APK не раздаём.

## Выпуск

```
git tag v0.0.1-beta && git push origin v0.0.1-beta
```

Дальше workflow сам собирает, подписывает и создаёт GitHub Release с
описанием из [RELEASE_NOTES.md](RELEASE_NOTES.md).

## Две грабли, на которые уже наступили

**Право на запись у токена Actions.** По умолчанию в аккаунте у
`GITHUB_TOKEN` стоит только чтение, и шаг «Create release» падает с
`403 Resource not accessible by integration` — притом что сборка и
подпись к этому моменту уже прошли успешно. Лечится один раз в
*Settings → Actions → General → Workflow permissions* →
**Read and write permissions**. Первый выпуск v0.0.1-beta из-за этого
пришлось создать вручную: APK взят из артефакта ручного запуска и
выложен командой `gh release create`.

**Отметка «pre-release».** Её не ставим, хотя версии бета. Сайт
texfi-hub читает выпуск через `/releases/latest`, а этот эндпоинт
пре-релизы пропускает: с отметкой карточка w0y осталась бы без версии
и без кнопки загрузки. У f0kus, files и m0ney по той же причине
бета-выпуски идут без неё.
