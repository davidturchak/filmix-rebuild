# Changelog

Release notes, newest first. `client/scripts/release.sh` reads this file: write
the new entries under `## Unreleased` before releasing, and the script fills in
the version and build number once the build tells it what they are.

The app shows every entry newer than the installed version when it offers an
update, so write for the person watching, not for the commit log.

## 0.6.15 (103)
- Обновление теперь честно говорит, что именно не получилось: «не удалось проверить» или «не удалось скачать», и показывает причину — раньше сорвавшаяся загрузка выглядела как неудачная проверка.
- Кнопка «Повторить» после сорвавшейся загрузки повторяет загрузку, а не проверку: до этого она ничего не делала.
- Ссылка на APK в манифесте обновлений исправлена — по прежнему адресу GitHub отдавал ошибку, и версия 0.6.14 находилась, но не скачивалась.

## 0.6.14 (100)
- Голосовой поиск теперь слушает по-русски. Раньше он брал язык системы, а телевизоры продаются с английским — поэтому русские названия почти никогда не распознавались.
- Язык можно сменить: «Настройки» → «Язык голосового поиска» — Русский, English или «Как в системе».
- Под кнопкой микрофона написано, на каком языке идёт распознавание, — так что английский вместо русского больше не остаётся незамеченным.

## 0.6.13 (96)
- Поиск больше не встречает вас крутящимся индикатором загрузки: при первом открытии сразу видно подсказку «Начните вводить название».
- Если по запросу ничего не нашлось, так и написано — «Ничего не найдено», вместо просьбы начать вводить название.

## 0.6.12 (93)
- Новый значок приложения: на плитке лаунчера теперь целиком написано «Filmix NG» — белым и фирменным оранжевым Filmix на тёмном фоне.
- Баннер на Android TV перерисован в том же стиле, чтобы плитка и баннер выглядели одинаково.

## 0.6.11 (91)
- Значок приложения в лаунчере Android TV: плитка больше не остаётся пустой.

## 0.6.10 (89)
- Технический выпуск: нужен, чтобы проверить обновление на телевизоре. Функциональных изменений нет.

## 0.6.9 (87)
- Кнопки обновления больше не теряют фокус: после нажатия курсор остаётся на них, а не уходит на боковое меню.

## 0.6.8 (85)
- Обновление показывает, что нового во всех версиях с момента вашей.

## 0.6.7 (83)
- Приложение само проверяет обновления при запуске и предлагает установить новую версию.

## 0.6.6 (79)
- Кнопку «Проверить обновления» снова можно нажать после проверки.

## 0.6.5 (77)
- Плитка приложения теперь видна в лаунчере Android TV.

## 0.6.4 (75)
- Главный экран: возврат из фильма ставит фокус на ту же карточку, а верх экрана снова доступен.

## 0.6.3 (73)
- Лайк и дизлайк на странице фильма: счётчики голосов и свой голос запоминается.

## 0.6.2 (71)
- Рамка фокуса на первом чипе строк («Фильтры», выбор сезона и озвучки) больше не обрезается слева.

## 0.6.1 (69)
- Исправления по код-ревью: галочки серий совпадают между озвучками и не обещают «Продолжить» без сохранённой позиции, старые позиции просмотра переносятся на новый формат ключей, окно фильтров больше не прячет нижний ряд под панелью навигации.

## 0.6.0 (67)
- Сериалы: просмотренные серии отмечаются галочкой, текущая выделена и в фокусе, кнопка «Смотреть» продолжает с нужной серии, сезон выбирается автоматически. Позиции просмотра больше не теряются при обновлении ссылок.

## 0.5.9 (65)
- Окно фильтров в каталоге больше не двигается и не меняет размер: прокручивается только содержимое

## 0.5.8 (63)
- Стабильность отзывов: устранены гонки загрузки и падения на некорректных данных

## 0.5.7 (61)
- Кнопка «Свернуть» в отзывах снова доступна с пульта

## 0.5.6 (59)
- Отзывы на странице фильма

## 0.5.5 (57)
- Новый раздел Настройки: выбор видеоплеера (встроенный или внешний — MX Player, VLC), качество и обновления в одном месте. Исправлена история просмотров: просмотры снова попадают в историю и список обновляется сразу.

## 0.5.4 (55)
- Detail: the watch button can no longer be squeezed out by a long title and a wide chip row. Search: a spoken query's results stay on screen. Favourites and history: a network failure now offers a retry instead of asking you to sign in, and pairing survives a font-size change. Playback: the resume position is saved when you leave. Filters: resetting keeps the focus inside the sheet.

## 0.5.3 (51)
- Playback: volume and play/pause keys work again while the controls are hidden. Favourites: a failure loading «Смотреть позже» no longer hides the favourites that did load. Filters: choosing a country no longer moves the chip out from under the focus ring.

## 0.5.2 (47)
- Search: the clear button can be reached with the remote — right out of the text field, centre to clear. Press back first if the on-screen keyboard is up, since it owns the D-pad while open.

## 0.5.1 (44)
- Playback: the on-screen controls can be summoned again with any key on the remote — CENTRE, the D-pad or play/pause — including after they time out mid-film.

## 0.5.0 (41)
- Android TV: rail tabs select on focus with a visible ring, BACK returns to Home, lists come back where you left them, search reaches its results, focus visible on every button, country filter previews Россия/Израиль/США/Корея/Франция/Германия, and pairing now populates history and favourites without a restart.

## 0.4.0 (27)
- Android TV: D-pad focus, TV layout sizing, working voice search

## 0.3.0 (23)
- filmix-ng: переименование, новая иконка. Серии: выбор сезона, озвучки и серии.

## 0.2.0 (19)
- Серии: выбор сезона, озвучки и серии. Каталог с сортировкой и фильтрами. История просмотров.
