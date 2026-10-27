# Informativa sulla Privacy – ScreenShade

_Ultimo aggiornamento: 24 ottobre 2026_

## 1. Funzionamento 100% offline
ScreenShade funziona **interamente offline**. L'applicazione **non dichiara né richiede** il permesso `android.permission.INTERNET` (né altri permessi di rete): non è tecnicamente in grado di connettersi alla rete, inviare dati o ricevere informazioni dall'esterno.

## 2. Dati personali e tracciamento
ScreenShade **non raccoglie, non trasmette e non conserva su server remoti** alcun dato personale, identificativo pubblicitario, informazione di utilizzo, telemetria, log diagnostico o report di crash. L'applicazione non include SDK di terze parti, librerie pubblicitarie, tracker analitici o sistemi di profilazione.

## 3. Archiviazione locale e sicurezza
Le sole informazioni memorizzate (preferenze di luminosità software, filtro colore cromatico, abilitazione tracciamento solare, lingua dell'interfaccia, tema grafico, flag di accettazione dell'avviso legale, stato del toggle di automazione e ultime coordinate geografiche approssimative) risiedono **esclusivamente nella sandbox locale dell'app** (`SharedPreferences`).
* **Protezione Backup:** Il backup di sistema è esplicitamente disabilitato (`android:allowBackup="false"`), impedendo l'estrazione automatica o non autorizzata dei dati tramite comandi ADB o backup cloud.
* **Cancellazione:** La disinstallazione dell'applicazione o la cancellazione dei dati dell'app da parte dell'utente elimina definitivamente tutte le impostazioni memorizzate.

## 4. Permessi utilizzati
| Permesso | Scopo tecnico | Dati trasmessi |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Mostrare il velo opaco di oscuramento e i filtri cromatici sopra le altre applicazioni | Nessuno |
| `RECEIVE_BOOT_COMPLETED` | Ripristinare lo stato del filtro all'accensione del dispositivo o dell'autoradio (se abilitato dall'utente) | Nessuno |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Mantenere attivo il servizio di overlay grafico in background | Nessuno |
| `POST_NOTIFICATIONS` | Mostrare la notifica di stato del servizio con pulsante fail-safe di arresto immediato | Nessuno |
| `ACCESS_COARSE_LOCATION` | Utilizzato solo su pressione esplicita dell'utente per ricavare latitudine e longitudine approssimative al fine di calcolare alba e tramonto tramite l'algoritmo matematico offline NOAA | Nessuno: le coordinate rimangono confinate nel dispositivo e non transitano mai su reti |

> [!NOTE]
> Per garantire la piena compatibilità con app di automazione standard (Tasker, MacroDroid) e comandi ADB shell senza richiedere permessi speciali a tali app, `AutomationReceiver` è protetto da un gate di sicurezza logico interno: tutti i comandi broadcast in arrivo vengono scartati e ignorati a meno che l'utente non abbia esplicitamente abilitato l'interruttore "Abilita Automazione Esterna" nelle impostazioni dell'app e accettato il disclaimer legale. Inoltre, l'attenuazione massima è limitata rigidamente all'85% per impedire black-out accidentali o malevoli dello schermo.

## 5. Link esterni e differenziazione release (F-Droid vs Google Play)
* Nelle build distribuite tramite F-Droid e GitHub, l'interfaccia può contenere un pulsante verso la pagina Ko-fi dello sviluppatore per consentire donazioni libere e spontanee a sostegno dello sviluppo open-source. La navigazione verso Ko-fi avviene interamente nel browser web predefinito dell'utente ed è disciplinata dalle policy sulla privacy di Ko-fi.
* Nelle build compilate per il Google Play Store, ogni collegamento o riferimento a piattaforme esterne di donazione è interamente disabilitato e rimosso dalla UI.

## 6. Minori e modifiche
ScreenShade non raccoglie dati di alcun tipo da alcun utente, inclusi i minori. Qualsiasi futuro aggiornamento della presente informativa sarà pubblicato in questo file all'interno del repository del progetto.

## 7. Contatti
Per domande o segnalazioni tecniche è possibile aprire una issue sul repository ufficiale del progetto.
