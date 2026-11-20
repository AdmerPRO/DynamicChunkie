# 90 propozycji rozwoju wydajności — master / Minecraft 26.3

Każdy punkt poniżej ma status **PROPOZYCJA**. Nie jest obietnicą przyrostu FPS ani
listą wdrożonych funkcji. Wykonane porządki i nowa architektura są opisane w
[raporcie Java](java-review.md). Priorytety: **P1** — zbadać najpierw,
**P2** — po potwierdzeniu kosztu, **P3** — większy eksperyment wymagający osobnych
zabezpieczeń i pomiarów. Cel może dotyczyć FPS, czasu klatki, alokacji, pamięci
lub utrzymania kodu; poprawa architektury sama w sobie nie gwarantuje FPS.

Dotychczasowy mały benchmark hot/soft nie wykazał dużego zysku z powielania
natywnego cullingu Sodium. Dlatego najpierw mierzymy koszt poszczególnych modułów.
Nowe algorytmy nie mogą zmieniać Simulation Distance ani serwerowej symulacji.
Nie przenosimy czytania świata i struktur renderera na przypadkowe wątki robocze.

## Sekcje, hot/soft i komendy renderera

1. **P1 — Unieważnianie konkretnych regionów Sodium.** Zastąpić globalne czyszczenie batchy listą regionów, których decyzje się zmieniły; testować oba kierunki przełączenia widoczności.
2. **P1 — Oddzielne przełączniki filtrowania rysowania i kolejki cold.** Pozwolić zachować odkładanie przebudów bez dodatkowego testowania sekcji, gdy natywny renderer już skutecznie je odrzuca.
3. **P1 — Przyrostowe aktualizowanie hot.** Obliczać różnicę zbiorów po przesunięciu kamery zamiast zawsze od nowa przechodzić pełną listę; zmiana projekcji musi wymusić odświeżenie.
4. **P1 — Rewizje lokalne sekcji.** Zmiana bloku daleko od kamery nie powinna usuwać wszystkich poprawnych decyzji o jej otoczeniu.
5. **P1 — Cache gotowości natywnego mesha.** Zapamiętywać wersjonowaną gotowość sekcji do czasu zdarzenia przebudowy lub usunięcia.
6. **P1 — Pomiar opłacalności dodatkowego cullingu.** Po serii zerowych pominięć przechodzić w rzadszy tryb próbny, z wybudzaniem przez zmiany kamery i świata.
7. **P2 — Indeks odwrotny sekcja → region komend.** Ułatwić skierowanie unieważnień z punktu 1 bez przeszukiwania wszystkich regionów.
8. **P2 — Jawny klucz kamery i projekcji.** Wersjonować pozycję, obrót, FOV i tryb kamery; nie używać snapshotu dla innego widoku.
9. **P2 — Bezpieczny margines czasowy widoczności.** Badać reuse dla rozszerzonego frustum, ale po wyjściu poza gwarantowany margines natychmiast dopuścić rysowanie.
10. **P2 — Liczniki sąsiedztwa soft.** Przetestować aktualizację tylko 26 sąsiadów zmienionych hot sekcji zamiast powtarzania 27 lookupów przy każdym zapytaniu; porównać dodatkową pamięć.
11. **P1 — Indeks cold według regionów.** Szybciej odnajdywać zadania, które właśnie weszły w widok, bez długiej rotacji odległych pozycji.
12. **P2 — Osobne limity żądań i gotowych uploadów.** Ograniczenie liczby zleceń nie ogranicza automatycznie kosztu zakończenia natywnych przebudów w tej samej klatce.
13. **P2 — Koalescencja zmian sekcji w czasie.** Łączyć wiele niepilnych powiadomień jednej cold sekcji; pilne zmiany nadal muszą przechodzić natywnie.
14. **P2 — Koszt sekcji zamiast samej liczby sekcji.** Używać dostępnej natywnej liczby elementów geometrii do priorytetu, bez skanowania bloków i bez dodatkowych chunk requests.
15. **P2 — Osobne polityki dla passów przezroczystych.** Oceniać zysk i poprawność, zachowując natywne sortowanie wody, szkła i innych przezroczystości.
16. **P2 — Priorytet widocznych zmian geometrii.** W kolejce odtwarzania traktować sekcje na ekranie i blisko kamery przed samym soft buforem.
17. **P3 — Indeks przestrzenny pełnych przesłon.** Przed ośmioma testami narożników odrzucać przesłony, których położenie wyklucza zasłonięcie celu.
18. **P3 — Hierarchia konserwatywnych testów sekcji.** Badać większą grupę AABB tylko wtedy, gdy cały jej zakres można bezpiecznie uznać za zasłonięty; nie ukrywać na podstawie próbki środka.

## Encje i block entities

19. **P1 — Budżet czasowy Entity Culling.** Oprócz liczby encji kontrolować nanosekundy zużyte w ticku, bo dziewięć długich raycastów nie kosztuje tyle co jeden krótki.
20. **P1 — Natywne frustum przed testami zasłonięcia.** Nie wykonywać kosztownych promieni dla obiektów, których renderer i tak obecnie nie narysuje; nie utrwalać takiej decyzji po obrocie kamery.
21. **P1 — Kolejka kandydatów według znaczenia.** Najpierw sprawdzać potencjalnie widoczne, kosztowne obiekty; odległy obiekt za kamerą może zaczekać.
22. **P1 — Pomijanie niezmienionego stanu encji.** Nie przeliczać promieni, gdy kamera, AABB i zależne sekcje zachowały właściwe wersje.
23. **P2 — Rewizje bloków dla decyzji o encjach.** Otworzenie drzwi lub zburzenie ściany powinno lokalnie unieważnić decyzję, bez polegania tylko na wieku cache.
24. **P2 — Współdzielone odczyty bloków w jednym ticku.** Sąsiednie promienie wielu mobów mogą korzystać z tego samego krótkotrwałego cache nieprzezroczystych bloków.
25. **P2 — Odrzucanie promieni po pierwszej potwierdzonej widoczności.** Dobierać kolejność próbek AABB do poprzedniej widocznej strony, zachowując wszystkie warunki konieczne do ukrycia.
26. **P2 — Brak globalnego odświeżania kolejki co pół sekundy.** Badać przyrostowe utrzymywanie kandydatów na podstawie bezpiecznych zdarzeń dodania/usunięcia encji.
27. **P2 — Adaptacyjne wygaszanie kosztu przy szybkim locie.** Gdy decyzje tracą ważność prawie co klatkę, ograniczyć samo próbkowanie zasłonięcia zamiast produkować bezużyteczny cache.
28. **P2 — Rzadziej przeliczać niezmienione limity typu.** Przy nieruchomej kamerze i nieruchomych obiektach zachować wynik selekcji; wejście/wyjście obiektu i ruch muszą go odświeżyć.
29. **P2 — Indeks kandydatów w sekcjach encji.** Ograniczyć obszar selekcji do obiektów istotnych dla odległości wizualnej, nie zmieniając ich obecności lub ticków w świecie.
30. **P2 — Histereza granicy dystansu encji.** Utrzymywać mały margines wejścia/wyjścia, aby kosztowny model nie pojawiał się i nie znikał przy minimalnym ruchu.
31. **P2 — Osobny dystans cieni encji.** Jeżeli dokładna wersja renderera daje bezpieczny punkt zaczepienia, ograniczyć odległe cienie bez ukrywania samych graczy i modeli.
32. **P2 — Osobny dystans nameplate'ów.** Opcjonalnie ograniczyć napisy dużych farm, z ochroną graczy i jawnie ważnych nazw.
33. **P2 — Osobny dystans dodatkowych warstw modeli.** Rozważyć kosmetyczne warstwy na odległych mobach, z domyślnym zachowaniem pełnej jakości i bez ingerencji w ticki.
34. **P2 — Block entities: tani dystans przed ekstrakcją kosztownych danych.** Zweryfikować, czy obecny hook jest wystarczająco wczesny dla skrzyń, znaków i modeli modowanych.
35. **P3 — Cache konserwatywnej widoczności block entities.** Osobny cache z poprawną invalidacją, bez traktowania dowolnego modelu jako mieszczącego się w jednym bloku.
36. **P3 — Koszt renderowania według typu encji.** Umożliwić budżet bazujący na zmierzonym koszcie modeli, a nie traktowaniu wszystkich sztuk identycznie; nie mierzyć każdej sztuki stale.

## Cząsteczki i jakość wizualna

37. **P1 — Natywna liczba aktywnych cząsteczek bez tekstu.** Sprawdzić wspierany accessor lub właściwe dane silnika, aby przy włączonym module usunąć countParticles → String → int.
38. **P1 — Odległość przed tworzeniem cząsteczki.** Zweryfikować, czy każdy wspierany typ trafia w hook przed alokacją obiektu; dla wyjątków wybrać wcześniejszy bezpieczny punkt.
39. **P2 — Tani test frustum dla kosmetycznych emitterów.** Nie odrzucać ważnego efektu ani cząsteczki, której trajektoria zaraz wejdzie w ekran.
40. **P2 — Budżety grup efektów.** Osobno traktować ambient, pogodę, farmy i efekty walki, aby lawa nie zużywała całego budżetu innych kosmetycznych efektów.
41. **P2 — Deterministyczne przerzedzanie burstów.** Rozkładać pominięcia w czasie i przestrzeni, zamiast przepuszczać wyłącznie pierwsze N cząsteczek danego ticka.
42. **P2 — Ograniczanie życia odległych kosmetycznych efektów.** Eksperyment opcjonalny; bez usuwania ważnych informacji o walce i bez zmiany serwerowych efektów.
43. **P2 — Limity regionalne dla emitterów.** Jeden intensywny punkt, np. farma, nie powinien zagłuszać całego budżetu wizualnego wokół gracza.
44. **P2 — Łączenie pracy nad statycznymi emitterami.** Zapamiętywać wynik taniego testu odległości/widoczności emitera przy niezmienionej kamerze i sekcji.
45. **P1 — Histereza jakości chmur.** Wymagać utrzymującego się przekroczenia progu, aby nie przełączać jakości przy małych wahaniach skali.
46. **P2 — Cooldown kosztownych zmian jakości.** Rozpoznać, które opcje wymagają natywnej przebudowy i nie traktować ich jak bezkosztowego mnożnika na każdą klatkę.
47. **P2 — Różne czasy powrotu jakości.** Szybsze ograniczanie po trwałym przeciążeniu i wolniejsze przywracanie po potwierdzonym zapasie, dla każdego modułu osobno.
48. **P3 — Opcjonalna jakość dalekich kosmetycznych warstw.** Dodawać tylko dla elementów z konkretnym bezpiecznym hookiem; nie podmieniać całego renderera ani domyślnie degradować obrazu.

## Analiza otoczenia i sterowanie RD

49. **P1 — Lokalne rewizje cache środowiska.** Aktualizacje odległego chunka nie powinny kasować wyniku promieni przy graczu, jeśli nie leżą na ich trasach.
50. **P1 — Oddzielić próbki geometryczne od ich oceny.** Zmiana profilu RD lub wag nie musi wymuszać nowych raycastów, jeżeli geometria i kamera się nie zmieniły.
51. **P1 — Twardy budżet czasu próbkowania.** Przy przekroczeniu budżetu zachować ostatni bezpieczny wynik i dokończyć pracę później; nie utożsamiać brakującej próbki z otwartym światem.
52. **P2 — Priorytet promieni przy niepewnej klasyfikacji.** Częściej badać kierunki, które mogą rozstrzygnąć tunel kontra wyjście, pozostawiając minimalny zestaw kierunków we wszystkich sytuacjach.
53. **P2 — Cache przejść promieni przez chunki.** Przy minimalnym ruchu ponownie używać tylko wersjonowanej części danych; nigdy nie prosić o brakujące chunki.
54. **P2 — Aktualizacja analizy po istotnej zmianie kamery.** Użyć poprawnie zawiniętego kąta yaw i unikać fałszywej dużej zmiany na granicy pełnego obrotu.
55. **P2 — Adaptacyjny horyzont predykcji.** Uzależnić go od prędkości i niepewności, z limitem dodatkowych promieni, zamiast zawsze płacić maksymalny koszt.
56. **P2 — Wspólna próbka kierunku wzroku i ruchu.** Gdy kierunki są praktycznie identyczne i spełniają ten sam margines dokładności, unikać zdublowanych raycastów.
57. **P2 — Osobny cooldown wzrostu RD po spadku wydajności.** Ograniczyć ponowne ładowanie dużego promienia po krótkim pojedynczym okresie poprawy.
58. **P2 — Obciążenie natywnej kolejki przebudów jako dodatkowy sygnał.** Wstrzymać dalszy wzrost zasięgu, gdy już zlecona geometria nie nadąża, z zachowaniem minimum użytkownika.
59. **P2 — Priorytet stabilnego frametime nad średnim FPS.** Rozróżniać stały limit klatek, serię kompilacji i pojedynczy GC; nie reagować na każdy przypadek tą samą zmianą RD.
60. **P3 — Kalibracja profili bez mapowania samego Y.** Zebrać powtarzalne sceny tuneli, otwartych jaskiń i szczytów, a następnie stroić wagi na ich pomiarach i testach poprawności.

## Telemetria, HUD i koszt samego moda

61. **P1 — Formatowanie HUD raz na 200–250 ms.** Cache tekstu oraz szerokości linii ograniczy String.format i pomiary fontu przy wysokim FPS; wykres może nadal poruszać się co klatkę.
62. **P1 — Opcjonalne pomiary kosztu modułów.** Zbierać czas analiz, wyboru encji i invalidacji w trybie diagnostycznym, bez stałych szczegółowych znaczników w każdym wywołaniu.
63. **P2 — Rzadsze odczyty ThreadMXBean.** Agregować czas CPU między próbkami i prawidłowo normalizować wynik względem liczby klatek; nie przedstawiać sumy wielu klatek jako czasu jednej.
64. **P2 — Wspólny budżet analiz na tick.** Analiza środowiska, przesłon i encji nie powinny jednocześnie zużywać niezależnych pełnych budżetów przy przeciążeniu.
65. **P2 — Rozłożenie zadań okresowych na różne ticki.** Unikać zbiegania się odświeżeń co 250/500/750 ms w jednym punkcie.
66. **P2 — Adaptacja budżetu według realnie oszczędzonej pracy.** Moduł z zerowym skutkiem i mierzalnym kosztem powinien dostawać mniej czasu niż skuteczny moduł.
67. **P2 — Asynchroniczne odczyty już gotowych timingów GPU.** Używać tylko wspieranego API i opóźnionych wyników; brak danych nie może wywoływać blokującej synchronizacji GPU.
68. **P2 — Lekkie znaczniki JFR.** Ułatwić zewnętrzne profilowanie konkretnych faz moda, z kontrolą częstotliwości i bez logowania każdej encji lub sekcji.
69. **P2 — Agregacja logów.** Grupować powtarzalne problemy kompatybilności lub niedostępne metryki, zamiast generować kosztowne komunikaty w pętli.
70. **P2 — Snapshot diagnostyczny tylko do odczytu.** HUD i eksport raportu powinny odczytywać spójne dane, a nie uruchamiać kosztowne analizy w trakcie rysowania.

## Pamięć i czas życia cache

71. **P1 — Rzeczywiste pomiary alokacji na farmach.** Porównać GC i bytes/tick przed i po nowym selektorze; liczba rekordów w cache nie zastępuje pomiaru alokacji.
72. **P2 — Zmniejszanie rozrośniętych tablic po wyjściu ze świata.** Zwolnić duży peak reservation w bezpiecznym momencie, nie na każdej klatce i nie przy każdym clear.
73. **P2 — Budżety metadanych niezależne od geometrii.** Osobno limitować wpisy analizy, decyzje encji i zadania cold, z jawną jednostką i rozróżnieniem estymacji od pomiaru.
74. **P2 — Przyrostowe usuwanie przeterminowanych wpisów.** Sprzątać ograniczoną liczbę wpisów na tick zamiast powodować duży skok pracy po długim locie.
75. **P2 — Usuwanie zależności do wyładowanych chunków.** Reagować na unload, aby stare referencje i nieprzydatne zadania nie czekały wyłącznie na TTL.
76. **P3 — Opcjonalna ewikcja cold geometrii.** To osobny eksperyment dla dokładnie obsługiwanego renderera; wymaga natywnych metod zwalniania i poprawnego odtworzenia po powrocie kamery.
77. **P3 — Bezpieczne wycofywanie buforów GPU.** Jeśli powstanie ewikcja, zwalniać zasoby dopiero w zgodnym z rendererem momencie; nie dodawać blokującego czekania na GPU.
78. **P3 — Cache zależny od szacowanego kosztu odtworzenia.** Droga geometria blisko gracza może być cenniejsza od dawno niewidzianej odległej sekcji; sprawdzić to dopiero po pomiarach z punktów 71–77.

## Architektura oraz wiarygodne pomiary

79. **P1 — Benchmark farm encji.** Sceny 100/1000/5000 sztuk, różne limity K i typy; mierzyć czas selekcji oraz klatki, a nie tylko liczbę ukrytych obiektów.
80. **P1 — Benchmark intensywnych cząsteczek.** Powtarzalny emiter i mieszanka ważnych oraz kosmetycznych efektów, z pomiarem alokacji i CPU.
81. **P1 — Trasy po wcześniej wygenerowanym świecie.** Ograniczyć wpływ worldgen przy porównaniu wariantów renderowania; osobno mierzyć koszt doładowywania nowych chunków.
82. **P1 — Więcej powtórzeń i analiza rozrzutu.** Losować kolejność wariantów, publikować surowe dane i przedziały niepewności zamiast oceniać zmianę po dwóch średnich.
83. **P2 — Rejestr możliwości adapterów.** Deklarować wspierane operacje i znane ograniczenia renderera, żeby wyłączać tylko niedostępny moduł zamiast przypadkowo cały system.
84. **P2 — Niemutowalny snapshot konfiguracji runtime.** Rozdzielić format JSON/UI od gotowych parametrów obliczeń; zachować migrację i odzyskiwanie oryginalnego RD.
85. **P2 — Osobny kontroler własności ustawienia RD.** Skupić przejęcie ustawienia, ręczne zmiany, przywracanie i awarie zapisu w jednym komponencie z testami przejść stanów.
86. **P2 — Typowane zdarzenia zmian renderowania.** Oddzielić zmianę bloków, gotowość mesha, zmianę kamery, reload zasobów i unload, zamiast traktować je jako jedną globalną rewizję.
87. **P2 — Zegar i budżet pracy jako zależności testowe.** Testować cooldown, timeout i presję CPU bez czekania na prawdziwy upływ czasu; nie wprowadzać frameworka DI tylko w tym celu.
88. **P2 — Rejestrowanie przyczyny odrzucenia.** Rozdzielić native frustum, dodatkowe zasłonięcie, dystans i limit typu, aby nie przypisywać tej samej oszczędności kilku modułom.
89. **P2 — Podpisane snapshoty do bezpiecznych obliczeń w tle.** Rozważać worker wyłącznie dla czystej matematyki na skopiowanych AABB/parametrach; przed użyciem porównać wersję świata i kamery, a stare wyniki odrzucić.
90. **P2 — Macierz testów realnych warunków.** Osobno porównać vanilla/Sodium, różne rozdzielczości, FPS cap, realną Elytrę, resource reload, teleport i serwer zdalny; wynik małego świata testowego nie zastąpi tych prób.

## Proponowana kolejność

Najpierw **79–82 i 62** (pomiar), następnie **1–2, 19–24, 37 i 61**
(najbardziej konkretne źródła dodatkowego kosztu), później lokalne rewizje i
przyrostowe kolejki. Ewikcja geometrii i bardziej rozbudowana occlusion hierarchy
powinny pozostać osobnymi eksperymentami po potwierdzeniu potrzeby. Dla każdej
zmiany zapisujemy koszt CPU, średni i p95/p99 frametime, poprawność obrazu oraz
zakres kompatybilności; decyzję podejmujemy na podstawie konkretnego scenariusza.
