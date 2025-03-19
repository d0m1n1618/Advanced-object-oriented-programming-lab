package com.lab.statistics; //Deklaracja pakietu

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class StatisticsService {
    //Ścieżka do katalogu, w którym znajdują się pliki do analizy
    private final String dirPath;
    //Liczba producentów, którzy będą przeszukiwać katalog w poszukiwaniu plików
    private final int liczbaProducentow;
    //Liczba konsumentów, którzy będą analizować zawartość plików
    private final int liczbaKonsumentow;
    //Liczba najczęściej występujących słów, które mają zostać uwzględnione w statystykach
    private final int liczbaWyrazowStatystyki;
    //Pula wątków do obsługi producentów i konsumentów
    private final ExecutorService executor;
    //Lista przyszłych wyników (Future) producentów
    private final List<Future<?>> producentFuture;
    //Flaga kontrolująca zatrzymanie procesu
    private final AtomicBoolean fajrant;
    //Kolejka blokująca do komunikacji między producentami a konsumentami
    private final BlockingQueue<Optional<Path>> kolejka;

    public StatisticsService(String dirPath, int liczbaProducentow, int liczbaKonsumentow, int liczbaWyrazowStatystyki) {
        this.dirPath = dirPath;
        this.liczbaProducentow = liczbaProducentow;
        this.liczbaKonsumentow = liczbaKonsumentow;
        this.liczbaWyrazowStatystyki = liczbaWyrazowStatystyki;

        //Tworzenie puli wątków o wielkości odpowiadającej liczbie producentów i konsumentów
        this.executor = Executors.newFixedThreadPool(liczbaProducentow + liczbaKonsumentow);

        //Inicjalizacja listy przechowującej przyszłe wyniki producentów
        this.producentFuture = new ArrayList<>();

        //Inicjalizacja flagi kontrolującej zatrzymanie pracy
        this.fajrant = new AtomicBoolean(false);

        //Inicjalizacja kolejki blokującej do przekazywania ścieżek plików
        this.kolejka = new LinkedBlockingQueue<>(liczbaKonsumentow);
    }

    public void startStatistics() {
        //Sprawdzenie, czy jakiś producent nadal działa - jeśli tak, nie uruchamiamy nowych zadań
        if (producentFuture.stream().anyMatch(f -> !f.isDone())) {
            System.out.println("Nie można uruchomić nowego zadania! Producent nadal działa.");
            return;
        }

        //Resetowanie flagi zatrzymania
        fajrant.set(false);

        //Czyszczenie listy producentów
        producentFuture.clear();

        final int przerwa = 60; //Czas oczekiwania producenta między kolejnymi iteracjami (sekundy)

        //Definicja zadania producenta
        Runnable producent = () -> {
            final String name = Thread.currentThread().getName();
            System.out.println("PRODUCENT " + name + " URUCHOMIONY ...");

            while (!Thread.currentThread().isInterrupted()) {
                if (fajrant.get()) { //Sprawdzenie, czy należy zakończyć pracę
                    try {
                        //Wysłanie sygnałów "poison pill" do konsumentów, aby zakończyli pracę
                        for (int i = 0; i < liczbaKonsumentow; i++) {
                            kolejka.put(Optional.empty());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                } else {
                    try {
                        //Przechodzenie przez wszystkie pliki w katalogu
                        Files.walkFileTree(Paths.get(dirPath), new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                if (file.toString().endsWith(".txt")) { //Sprawdzenie, czy plik ma rozszerzenie .txt
                                    try {
                                        //Dodanie pliku do kolejki
                                        kolejka.put(Optional.of(file));
                                        System.out.println("Producent dodał plik: " + file);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    //Uśpienie producenta na określony czas
                    TimeUnit.SECONDS.sleep(przerwa);
                } catch (InterruptedException e) {
                    System.out.println("Przerwa producenta " + name + " przerwana!");
                    if (!fajrant.get()) Thread.currentThread().interrupt();
                }
            }

            System.out.println("PRODUCENT " + name + " SKOŃCZYŁ PRACĘ");
        };

        //Definicja zadania konsumenta
        Runnable konsument = () -> {
            final String name = Thread.currentThread().getName();
            System.out.println("KONSUMENT " + name + " URUCHOMIONY ...");

            try {
                while (true) {
                    Optional<Path> optPath = kolejka.take(); //Pobranie pliku z kolejki
                    if (!optPath.isPresent()) { //Jeśli "poison pill", to zakończ działanie
                        System.out.println("KONSUMENT " + name + " OTRZYMAŁ Poison Pill – KOŃCZY PRACĘ.");
                        break;
                    }

                    Path path = optPath.get();
                    System.out.println("KONSUMENT " + name + " PRZETWARZA PLIK: " + path);

                    //Obliczenie statystyk słów w pliku
                    Map<String, Long> countedWords = getLinkedCountedWords(path);
                    System.out.println("Statystyka dla " + path + ": " + countedWords);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("KONSUMENT " + name + " ZAKOŃCZYŁ PRACĘ.");
        };

        //Uruchomienie producentów w osobnych wątkach
        for (int i = 0; i < liczbaProducentow; i++) {
            Future<?> pf = executor.submit(producent);
            producentFuture.add(pf);
        }

        //Uruchomienie konsumentów w osobnych wątkach
        for (int i = 0; i < liczbaKonsumentow; i++) {
            executor.execute(konsument);
        }
    }

    //Metoda zatrzymująca statystyki
    public void stopStatistics() {
        fajrant.set(true); //Ustawienie flagi zatrzymania
        for (Future<?> f : producentFuture) {
            f.cancel(true); //Przerwanie pracy producentów
        }
    }

    //Metoda zatrzymująca działanie wszystkich wątków
    public void shutdown() {
        executor.shutdownNow();
    }

    //Metoda analizująca zawartość pliku i zwracająca mapę najczęściej występujących słów
    private Map<String, Long> getLinkedCountedWords(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.lines()
                    .flatMap(line -> Arrays.stream(line.split("\\s+"))) //Podział linii na słowa
                    .map(word -> word.replaceAll("[^a-zA-Z0-9ąęóśćżńźłĄĘÓŚĆŻŃŹŁ]", "").toLowerCase()) //Czyszczenie słów
                    .filter(word -> word.matches("[a-zA-Z0-9ąęóśćżńźłĄĘÓŚĆŻŃŹŁ]{3,}")) //Pomijanie krótkich słów
                    .collect(Collectors.groupingBy(w -> w, Collectors.counting())) //Zliczanie wystąpień słów
                    .entrySet().stream() //Zestaw kolejnych elementów (klucz1, wartosc1), (klucz2, wartosc2)
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())) //Sortowanie malejąco
                    .limit(liczbaWyrazowStatystyki) //Ograniczenie do X najczęstszych słów
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (k, v) -> { throw new IllegalStateException("Błąd! Duplikat klucza " + k); },
                            LinkedHashMap::new
                    ));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
