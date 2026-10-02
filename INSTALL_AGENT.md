# JukeboxRadio — установка на сервер (инструкция для агента)

Цель: поставить плагин JukeboxRadio на сервер Paper 26.3 и проверить, что работает меню
проигрывателя, ресурс-пак и звук. Шаги выполняй по порядку. Прежде чем что-то перезапускать,
удалять или открывать порты, спроси владельца сервера.

## 0. Что должно быть на сервере

| Требование | Как проверить |
|---|---|
| Paper **26.3** | в консоли `version` → `26.3-…` |
| Java **25** | `java -version` той JVM, что запускает сервер |
| Плагин **Simple Voice Chat** (Bukkit/Paper, 2.6.24+) | `plugins/voicechat-*.jar`, в консоли `plugins` → `voicechat` |
| Исходящий интернет с сервера | до `api.spotify.com`, `open.spotify.com`, `*.youtube.com`, `*.googlevideo.com`, `github.com` |
| Свободный **TCP-порт 8166** | для раздачи ресурс-пака меню (можно сменить) |
| Открытый **UDP-порт 24454** | его слушает Simple Voice Chat, без него игроки не услышат звук |

Если Simple Voice Chat не стоит, скачай версию для Paper 26.3 с
https://modrinth.com/plugin/simple-voice-chat (loader: paper / bukkit) и положи в `plugins/`.
У игроков на клиенте тоже должен стоять мод Simple Voice Chat.

GameUI, ProtocolLib и другие GUI-плагины **не нужны**.

## 1. Получить jar

Готовый файл: `build/libs/JukeboxRadio-1.1.jar` (~33 МБ, внутри нативные библиотеки декодера
для всех ОС).

Если jar нет или код менялся, собери из исходников:

```bash
cd JukeboxRadio
./gradlew build
```

Gradle сам скачает JDK 25. Сборка должна закончиться `BUILD SUCCESSFUL`, и все тесты должны
пройти.

## 2. Установить

1. Останови сервер штатно, командой `stop` в консоли.
2. Скопируй `JukeboxRadio-1.1.jar` в `plugins/`. Если там лежит старая версия JukeboxRadio,
   удали её, чтобы не было двух jar.
3. Запусти сервер. При первом старте плагин создаст:
   - `plugins/JukeboxRadio/config.yml` — настройки;
   - `plugins/JukeboxRadio/pack/jukeboxradio.zip` — сгенерированный ресурс-пак меню;
   - `plugins/JukeboxRadio/bin/yt-dlp` (или `yt-dlp.exe`) — скачивается автоматически, если yt-dlp
     нет в PATH.
4. Останови сервер и переходи к настройке.

## 3. Настроить `plugins/JukeboxRadio/config.yml`

Обязательно:

```yaml
pack:
  mode: self-host
  port: 8166
  public-address: play.example.net   # IP или домен, по которому ИГРОКИ заходят на сервер
```

- `public-address: auto` работает только если в `server.properties` задан внешний `server-ip`.
  Иначе игрокам уйдёт ссылка на `127.0.0.1`, и пак у них не скачается.
- За NAT или Docker сделай проброс `8166/tcp` наружу. Внутри контейнера должно остаться
  `bind-ip: "0.0.0.0"`.
- Если открыть порт нельзя, выбери один из вариантов:
  - `mode: external` — залей `pack/jukeboxradio.zip` на любой хостинг с прямой ссылкой и укажи её в
    `pack.external-url`. После каждого обновления плагина zip нужно перезаливать: SHA-1 меняется.
  - `mode: none` — влей содержимое zip в серверный ресурс-пак (всё лежит в пространстве имён
    `jukeboxradio`, с чужими файлами не пересекается).

Желательно — ключи Spotify, чтобы поиск в меню шёл по Spotify. Без них поиск идёт по YouTube
Music, а ссылки Spotify всё равно открываются.

```yaml
spotify:
  client-id: "…"
  client-secret: "…"
```

Ключи выдаёт владелец сервера: developer.spotify.com/dashboard → Create app. Для приложений в
Development Mode Spotify требует Premium у владельца приложения. **Не придумывай и не подставляй
чужие ключи.**

Если с сервера не открывается YouTube (проверь `curl -sI https://www.youtube.com`), нужен
HTTP-прокси. Через него пойдут yt-dlp, звук, поиск, Spotify и обложки:

```yaml
network:
  proxy: "http://127.0.0.1:3128"   # только http://, без логина; пусто — напрямую
```

При старте в логе появится `All radio traffic goes through proxy http://…`.

Остальное можно оставить по умолчанию:
- `radio.distance` — на сколько блоков слышно радио, по умолчанию 48;
- `radio.max-queue` — максимум треков в очереди;
- `menu.require-pack: true` — не открывать меню без пака.

Запусти сервер.

## 4. Проверить по логу

После старта в консоли должно быть:

```
[JukeboxRadio] Enabling JukeboxRadio v1.1
[JukeboxRadio] Radio pack served at http://<public-address>:8166/jukeboxradio-<hash>.zip
[JukeboxRadio] Simple Voice Chat API connected
[voicechat] Registering events for 'jukeboxradio'
[JukeboxRadio] yt-dlp downloaded to plugins/JukeboxRadio/bin/yt-dlp   (или "yt-dlp found on PATH")
```

Разбор проблем:

| Строка в логе | Что делать |
|---|---|
| `Simple Voice Chat is not installed: the radio will be silent` | поставить voicechat (шаг 0) |
| `Could not start the radio pack server on port 8166` | порт занят: сменить `pack.port` и перезапустить |
| `pack.public-address is AUTO and server-ip is empty` | задать `pack.public-address` (шаг 3) |
| `Could not download yt-dlp` | нет доступа к github.com: поставить yt-dlp вручную и указать `audio.yt-dlp.path` |
| `Spotify keys are empty` | не ошибка: поиск идёт по YouTube Music, пока не заданы ключи |
| `network.proxy ignored: …` | неверный адрес прокси: нужен `http://хост:порт`; пока плагин ходит напрямую |
| `In-place title updates unavailable` | меню работает, но курсор прыгает при каждом обновлении: сообщить разработчику, какая версия Paper |

Проверь, что пак отдаётся **снаружи**, с машины вне сервера (URL возьми из строки
`Radio pack served at`):

```bash
curl -s -o /dev/null -w "%{http_code} %{size_download}\n" "http://<public-address>:8166/jukeboxradio-<hash>.zip"
```

Ожидается `200` и размер около `870000`.

## 5. Проверить в игре

Нужен любой игрок онлайн.

1. При входе клиент попросит принять ресурс-пак. Принять.
   В логе не должно быть `Radio pack FAILED_DOWNLOAD`.
2. Поставь проигрыватель (jukebox) и нажми по нему **Shift + ПКМ**. Откроется окно сундука со
   своей графикой: проигрыватель слева, рамка обложки, кнопки, панель справа.
3. Поставь трек. Можно из консоли от имени игрока (нужно право `jukeboxradio.admin`, у консоли оно
   есть):

   ```
   radio open <игрок> <x> <y> <z>
   radio as <игрок> play https://www.youtube.com/watch?v=dQw4w9WgXcQ
   ```

   Первая команда открывает проигрыватель по координатам, вторая добавляет трек в очередь.
4. Через 2–6 секунд правая панель покажет «Играет · YouTube», пойдёт время, центр пластинки
   окрасится в цвет обложки. С модом Voice Chat рядом с блоком будет слышна музыка.
5. Проверь ссылку Spotify:

   ```
   radio as <игрок> play https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
   ```

   В очередь должно встать ~50 треков.

Если меню открылось квадратами или не открылось с сообщением про ресурс-пак, значит у игрока не
загружен пак. Проверь `pack.public-address` и порт, а у игрока — в списке серверов «Наборы
ресурсов: Включены».

## 6. Права

| Право | По умолчанию | Даёт |
|---|---|---|
| `jukeboxradio.use` | всем | открывать проигрыватель, `/radio play/search/skip/pause/stop/volume/status` |
| `jukeboxradio.admin` | операторам | `/radio stopall`, `/radio open <игрок> x y z`, `/radio as <игрок> …` |

## 7. Обновление и откат

- **Обновление:** остановить сервер, заменить jar, запустить. `config.yml` сохранится. Пак
  пересоберётся сам, и игроки скачают его заново при входе.
- **Откат или удаление:** остановить сервер, удалить `plugins/JukeboxRadio-*.jar`.
  Папку `plugins/JukeboxRadio/` можно оставить: в ней конфиг, yt-dlp и пак, данных игроков нет.
  Очереди хранятся только в памяти и при перезапуске пропадают.

## 8. Частые проблемы

| Симптом | Причина и решение |
|---|---|
| «YouTube отвечает слишком долго» / «Сервис не отвечает», YouTube с сервера не открывается | YouTube заблокирован у провайдера: задать `network.proxy` (шаг 3) |
| Трек долго грузится, потом «YouTube не отдал аудио» | yt-dlp устарел: при старте он обновляется сам (`audio.yt-dlp.auto-update: true`), иначе выполнить `plugins/JukeboxRadio/bin/yt-dlp -U` |
| «YouTube просит вход» / `Sign in to confirm you're not a bot` | IP сервера помечен YouTube: передать yt-dlp cookies: `audio.yt-dlp.extra-args: ["--cookies", "/путь/cookies.txt"]` |
| Звука нет, меню работает | нет мода Voice Chat у игрока или закрыт UDP 24454; громкость «Радио» в настройках Voice Chat на нуле |
| Чужой плейлист Spotify не открывается | при `spotify.embed-fallback: false` Spotify отдаёт треки только своих плейлистов: вернуть `true` |
| Предупреждение Java про `restricted method … NativeLibraryLoader` | безвредно; убирается флагом JVM `--enable-native-access=ALL-UNNAMED` |

Подробное описание работы — в `README.md`, устройство интерфейса — в `DESIGN.md`.
