# Политика конфиденциальности Autoban

Действует с 27 ноября 2026 г. Разработчик — umolab.

Autoban — приложение для Android, которое проверяет входящие звонки по правилам пользователя, сбрасывает и блокирует спам-звонки, ведёт журнал звонков и, по желанию пользователя, сообщает о звонках в Telegram.

## Какие данные обрабатываются

- **Сведения о входящих звонках**: номер, время, SIM, на которую пришёл звонок, подпись оператора (маркировка) и решение приложения. Источники — системный фильтр звонков Android, журнал вызовов (разрешение «Журнал вызовов»), уведомление звонилки о входящем (доступ к уведомлениям).
- **Имя контакта** для номера звонящего (разрешение «Контакты») — чтобы показать, кто звонит.
- **Правила, бан-лист и белый список**, которые создаёт пользователь.
- **Настройки Telegram**: токен бота и chat_id, которые вводит пользователь.
- **Входящие SMS** — только в версии приложения с GitHub и только если пользователь включил пересылку SMS. Версия из Google Play SMS не читает.

## Где данные хранятся

Журнал, правила, списки и настройки хранятся только на устройстве пользователя. Токен бота хранится в зашифрованном виде (ключ Android Keystore). У приложения нет собственного сервера; разработчик не получает никаких данных пользователя. В приложении нет рекламы и аналитики.

## Какие данные передаются и кому

- **zvonili.com** — номер звонящего не из контактов передаётся этому сайту, чтобы получить открытые отзывы о номере. Функцию можно выключить в настройках («Пробивать номер»).
- **Telegram** — только если пользователь подключил своего бота: сведения о звонках (номер, имя контакта, подпись оператора, решение, отзывы) и, в версии с GitHub при включённой пересылке, текст входящих SMS отправляются через Telegram Bot API в чат пользователя с его ботом. На эти данные распространяется [политика конфиденциальности Telegram](https://telegram.org/privacy).

Данные не продаются и не передаются третьим лицам для рекламы или иных целей.

## Срок хранения и удаление

Журнал хранится столько дней, сколько указано в настройках (по умолчанию 90), и может быть очищен вручную (Настройки → Журнал → Очистить). Кэш отзывов о номерах хранится до 3 дней. Удаление приложения удаляет все его данные с устройства. Сообщения, отправленные в Telegram, пользователь удаляет в самом Telegram.

## Разрешения

| Разрешение | Зачем |
|---|---|
| Роль «Фильтр звонков» | Проверять входящие звонки и сбрасывать спам |
| Телефон (состояние) | Определять, на какую SIM пришёл звонок, и следить за звонком |
| Журнал вызовов | Узнать номер звонящего и подпись оператора после звонка |
| Контакты | Показать имя звонящего из контактов |
| Управление звонками | Сбросить звонок по правилу или кнопкой в Telegram |
| Доступ к уведомлениям | Прочитать подпись оператора в уведомлении о входящем |
| Интернет | Отзывы о номере и сообщения в Telegram |
| SMS (только версия с GitHub) | Пересылать входящие SMS в Telegram, если пользователь это включил |

## Дети

Приложение не предназначено для детей и не собирает сознательно данные детей.

## Изменения

Изменения этой политики публикуются в этом файле: https://github.com/BlkDem/autoban/blob/main/PRIVACY.md

## Связь

Вопросы — через [Issues](https://github.com/BlkDem/autoban/issues) репозитория.

---

# Autoban Privacy Policy

Effective November 27, 2026. Developer: umolab.

Autoban is an Android app that screens incoming calls against the user's rules, rejects and blocks spam calls, keeps a call journal and, if the user chooses, reports calls to Telegram.

**Data processed**: incoming call details (number, time, SIM, operator caller label, the app's decision) from the Android call screening service, the call log and the dialer's incoming-call notification; the contact name for the caller's number; the user's rules and block/allow lists; the Telegram bot token and chat ID entered by the user; incoming SMS — only in the GitHub build and only when the user enables SMS forwarding (the Google Play build does not read SMS).

**Storage**: all data stays on the device. The bot token is encrypted with an Android Keystore key. The app has no server of its own; the developer receives no user data. No ads, no analytics.

**Sharing**: the caller's number (for numbers not in contacts) is sent to zvonili.com to fetch public reviews — this can be turned off in settings. If the user connects their own Telegram bot, call details (and, in the GitHub build with forwarding enabled, SMS text) are sent via the Telegram Bot API to the user's chat with that bot; Telegram's privacy policy applies. Data is never sold or shared for advertising.

**Retention and deletion**: the journal is kept for the number of days set in settings (90 by default) and can be cleared at any time; the review cache is kept up to 3 days. Uninstalling the app removes all its data from the device.

**Children**: the app is not directed at children.

**Contact**: via [Issues](https://github.com/BlkDem/autoban/issues).
