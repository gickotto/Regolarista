# Regolarista v0.2 — aggiornamento GNSS e parziale

## Novità della v0.2

- **Barra fluida tra aggiornamenti GNSS**: la progressiva stimata avanza ogni 100 ms usando la velocità rilevata dal GNSS, invece di restare ferma fino al fix successivo. Quando arriva il fix, la differenza viene assimilata gradualmente (costante di tempo ~0,65 s); la previsione viene fermata dopo 1,6 s senza aggiornamenti, per evitare deriva illimitata. Il dato GNSS non è più applicato di colpo alla barra, salvo inizializzazione dopo una nuova partenza.
- **Odometro parziale**: nuovo indicatore in km a due decimali e pulsante `AZZERA PARZIALE`. La progressiva totale non viene azzerata. Il parziale riparte da zero a ogni nuova prova e quando si cambia traccia. Le correzioni manuali ±10 m influenzano entrambi gli odometri, mantenendo la calibrazione.
- **CSV**: aggiunte le colonne `smoothed_progress_m` e `partial_m` (le colonne precedenti sono conservate).
- **GitHub Actions**: workflow con Ubuntu 24.04, setup-android v4 e caricamento Artifact denominato `Regolarista-v0.2-debug-apk`.

## Installazione/aggiornamento del repository GitHub

Estrarre lo ZIP e **caricare il contenuto della cartella `Regolarista-v0.1` nella radice del repository** (inclusa la directory nascosta `.github`). Sostituire i file omonimi; non creare una cartella aggiuntiva nel repository.

In Actions → Build Regolarista APK → Run workflow, avviare una nuova compilazione e scaricare l'Artifact `Regolarista-v0.2-debug-apk` dalla pagina della singola esecuzione completata. Estrarre `app-debug.apk`.

## Test locali senza SDK Android

```bash
javac -d out app/src/main/java/it/regolarista/app/{RegularityEngine,ProgressPredictor}.java test/{EngineTest,ProgressPredictorTest}.java
java -cp out EngineTest
java -cp out ProgressPredictorTest
```

## Attenzione

La previsione tra i fix rende fluida la visualizzazione, **non aumenta la precisione assoluta del GNSS**. I salti di posizione molto grandi, i tratti fuori traccia e i ritardi del sensore possono ancora causare errori di misura. Verificare a veicolo fermo e poi su circuito/area sicura. La compilazione APK Android non è stata eseguita in questo ambiente.

---

# Regolarista 0.1 — Android (progetto sorgente)

Applicazione Android **nativa Java, offline, senza librerie runtime esterne** dedicata all'allenamento nelle prove di regolarità a media. **L'APK non è incluso**, perché nell'ambiente dove è stato creato questo progetto non erano disponibili Android SDK/Gradle né accesso alla rete per scaricarli.

## Funzioni implementate

- Barra anticipo (verde, sinistra) / ritardo (rosso, destra), graduata a 0,1 s nella zona centrale ±1 s e compressa sino a ±3 s; valore numerico al decimo.
- Calcolo teorico del tempo su sequenze di cambi media (`0=45`, `4000=38`, metri=km/h).
- Suggerimento velocità di recupero sull'orizzonte **3 secondi**, comprendente i cambi media; mostra `NON FATTIBILE` sopra il limite configurato (90 km/h di default, **non un limite stradale**).
- Segnale acustico periodico: 1 Hz se in media; sino a 2 Hz se in anticipo di almeno 1 s; rallenta con il ritardo sino a 1/3 Hz da 2 s. Pulsante di esclusione.
- Odometro progressivo a 0,01 km (10 m) con regolazione manuale ±10 m, separata dal contatore GNSS di base.
- Registrazione GNSS Android con servizio in primo piano, aggiornamento visualizzazione, file CSV di sessione ed esportazione con selettore di file Android.
- Importazione GPX (`trkpt` e `rtept`) tramite document picker, memorizzazione del permesso di accesso, proiezione sulle sezioni di traccia e controllo anti-salto in tornanti.
- Senza traccia: somma filtrata delle distanze successive GNSS.
- Partenza manuale, a orario locale HH:mm:ss e geografica (attraversamento della linea perpendicolare all'inizio del GPX, nel verso della traccia).

## Compilazione

**Metodo 1 — GitHub Actions**, non richiede installare Android Studio: carica l'intera cartella in un repository GitHub (i file nascosti `.github/workflows/android.yml` devono essere inclusi), apri **Actions → Build Regolarista APK → Run workflow**. Al termine scarica l'artefatto `Regolarista-v0.1-debug-apk`, decomprimilo e installa `app-debug.apk` su Android. È una APK debug firmata automaticamente con chiave di debug di CI, non una release firmata per pubblicazione.

**Metodo 2 — Android Studio**: apri la cartella `Regolarista-v0.1`, installa Android SDK Platform 35 e configura JDK 17. Avvia `:app:assembleDebug` (Gradle 8.9). Il file atteso sarà `app/build/outputs/apk/debug/app-debug.apk`.

**Metodo 3 — CLI** con Gradle 8.9 e Android SDK 35 installati:

```sh
gradle :app:assembleDebug
```

## Procedura di prova

1. Autorizza posizione precisa per l'app e attiva il GPS. Consenti la notifica per mantenere evidente la registrazione.
2. In **MEDIE** configura `0=45` e altri cambi media, se necessari.
3. In **TRACCIA** seleziona GPX, oppure **Allenamento senza traccia**.
4. Seleziona **PARTENZA** per avvio manuale o **AUTO START** per partenza a orario/geografica.
5. Durante la prova leggi la barra, l'odometro e la velocità di recupero; usa ±10 m per correggere la progressiva.
6. Premi **STOP** e **ESPORTA SESSIONE CSV**.

## Limiti importanti della v0.1

- Il progetto sorgente è stato creato e testato **parzialmente**, ma **non compilato come app Android né testato su un telefono**. La compilazione in GitHub Actions è prevista ma non verificata qui.
- L'errore temporale dipende da precisione e frequenza dei campioni GNSS; **nessuna garanzia al decimo di secondo** con smartphone soltanto.
- Nessun editing grafico del GPX, nessuna mappa live, nessun GNSS esterno, nessuna gestione di punteggi o controlli virtuali: funzioni future.
- In modalità senza traccia, l'odometro GNSS è indicativo e sensibile a multipath e perdita fix; non corrisponde necessariamente a quello ufficiale.
- Per sicurezza, in caso di perdita di matching sulla traccia la progressiva viene **congelata** e appare una segnalazione; al recupero del matching possono comparire salti. Il segnale audio non sostituisce le informazioni ufficiali di gara.
- La partenza geografica richiede due fix ai lati della linea di partenza, percorrenza nella direzione iniziale e un fix entro 18 m dal punto iniziale: è un trigger **indicativo**, non una fotocellula.
- Il servizio usa i permessi di localizzazione con notifiche; non è stato testato rispetto alle politiche aggressive di risparmio energetico del produttore. Se Android termina il processo, la sessione corrente non viene ripristinata automaticamente.
- Per decisioni sulla velocità valgono sempre Codice della Strada, limiti locali, traffico e condizioni di sicurezza. Il limite configurabile del suggerimento non determina automaticamente il limite legale su ogni strada.
- L'app è un dispositivo di **allenamento**; verificare regole e dispositivi ammessi prima di un eventuale impiego in gara.

## Test della logica senza Android SDK

```sh
mkdir -p test/classes
javac -d test/classes app/src/main/java/it/regolarista/app/RegularityEngine.java test/EngineTest.java
java -cp test/classes EngineTest
```

