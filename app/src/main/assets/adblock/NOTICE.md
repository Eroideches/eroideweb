# Liste di filtri incluse

Questi file provengono dal progetto EasyList e sono distribuiti con l'app
**senza modifiche**:

| File | Fonte | Licenza |
|------|-------|---------|
| `easylist.txt` | https://easylist.to/easylist/easylist.txt | GPLv3 / CC BY-SA 3.0 |
| `easyprivacy.txt` | https://easylist.to/easylist/easyprivacy.txt | GPLv3 / CC BY-SA 3.0 |
| `easylist-italy.txt` | https://easylist.to/easylist/easylistitaly.txt | GPLv3 / CC BY-SA 3.0 |

Snapshot incluso in questa build: 16 novembre 2026.

Le liste non vengono aggiornate tramite rete: cambiano solo quando esce una
nuova versione dell'app. Il codice di parsing (`FilterListParser`) applica
esclusivamente le regole di rete per host (`||dominio^`, eccezioni `@@||`);
regole cosmetiche e regole legate al percorso non sono supportate.

Per aggiornare: sostituire i tre file e ricompilare.
