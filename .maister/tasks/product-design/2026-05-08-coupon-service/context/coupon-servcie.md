# Zadanie rekrutacyjne backend

Celem zadania jest zaprojektowanie i zaimplementowanie REST-owego serwisu odpowiedzialnego za
zarządzanie kuponami rabatowymi. System powinien udostępniać następujące funkcjonalności:
- rejestrację użycia kuponu przez użytkownika, 
- tworzenie nowego kuponu (obsługa uwierzytelniania nie jest wymagana).

Każdy kupon powinien zawierać następujące informacje:
- unikalny kod kuponu, 
- datę utworzenia,
- maksymalną liczbę możliwych użyć,
- bieżącą liczbę użyć, 
- kraj, dla którego kupon jest przeznaczony.

Wymagania biznesowe:
- Kod kuponu powinien być unikalny, wielkość znaków nie ma znaczenia (WIOSNA i wiosna
traktujemy jak ten sam). 
- Wykorzystanie kuponu powinno być limitowane maksymalną liczbą użyć - kto pierwszy ten
lepszy.
- Kraj zdefiniowany w kuponie ogranicza użycie kuponu tylko do osób z danego kraju (na
podstawie adresu IP - można wykorzystać dowolną darmową usługę do tego). 
- Gdy kupon osiągnął maksymalną liczbę zużyć, próby użycia go powinny zwracać stosowną
informację w zwrotce. Tak samo, gdy podany kod kuponu nie istnieje, próba zużycia
przychodzi z niedozwolonego kraju lub użytkownik zużył już dany kupon.
- (Opcjonalnie, dla chętnych) Jeden użytkownik może wykorzystać kupon tylko raz – request
powinien zawierać identyfikator użytkownika (dowolny) oraz kod kuponu do wykorzystania.

Rozwiązanie powinno byś skalowalne. Dane powinny być zapisywane w bazie danych. Serwis
powinien być zaimplementowany w Java lub Kotlin. Projekt powinien być możliwy do zbudowania za
pomocą Maven lub Gradle. Możesz wspierać się dowolnymi,darmowymi, łatwo dostępnymi
technologiami (silniki BD, biblioteki, frameworki).

Przygotowując rozwiązanie zadania, pamiętaj, że zależy nam nie tylko na działającym kodzie, ale
również na jego jakości i podejściu do projektowania. Oczekujemy, że zaprezentujesz styl pracy jak
najbardziej zbliżony do tego, jakbyś realizował zadanie w realnym projekcie produkcyjnym.

Zachęcamy do stosowania dobrych praktyk programistycznych, przemyślanej architektury,
odpowiednich wzorców projektowych oraz rozwiązań technologicznych, które pokazują Twoje
zrozumienie tworzenia oprogramowania gotowego do wdrożenia. Unikaj uproszczonych
implementacji tworzonych jedynie na potrzeby spełnienia minimalnych wymagań zadania.

Cenimy przejrzystość, czytelność i jakość kodu, a także uwzględnienie kontekstu, że dany system
mógłby funkcjonować w wielowątkowym środowisku produkcyjnym.

Zależy nam na tym, aby kod był tworzony samodzielnie, bez wspomagania sztuczną inteligencją.
Prosimy o umieszczenie projektu na dowolnym repozytorium i udostępnienie nam linku.